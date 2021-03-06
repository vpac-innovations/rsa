package org.vpac.worker;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.client.ClusterClientReceptionist;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator.Put;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.vpac.ndg.common.datamodel.CellSize;
import org.vpac.ndg.common.datamodel.TaskState;
import org.vpac.ndg.query.filter.Foldable;
import org.vpac.ndg.query.stats.VectorCats;
import org.vpac.ndg.query.stats.Ledger;
import org.vpac.worker.Job.Work;
import org.vpac.worker.Job.WorkInfo;
import org.vpac.worker.master.WorkResult;
import org.vpac.worker.master.Ack;
import org.vpac.worker.MasterDatabaseProtocol.JobUpdate;
import org.vpac.worker.MasterDatabaseProtocol.Fold;
import org.vpac.worker.MasterWorkerProtocol.*;
import scala.Option;
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.FiniteDuration;

public class Master extends UntypedActor {

	public static String ResultsTopic = "results";
	public static Props props(FiniteDuration workTimeout) {
		return Props.create(Master.class, workTimeout);
	}

	private final FiniteDuration workTimeout;
	private final ActorRef mediator = DistributedPubSub.get(
			getContext().system()).mediator();
	private final LoggingAdapter log = Logging.getLogger(getContext().system(),
			this);
	private final Cancellable cleanupTask;
	private HashMap<String, WorkerState> workers = new HashMap<String, WorkerState>();
	private Queue<Work> pendingWork = new LinkedList<Work>();
	private Set<String> workIds = new LinkedHashSet<String>();
	private Map<String, WorkInfo> workProgress = new HashMap<String, WorkInfo>();
	Cluster cluster = Cluster.get(getContext().system());

	public Master(FiniteDuration workTimeout) {
		this.workTimeout = workTimeout;
		ClusterClientReceptionist.get(getContext().system()).registerService(getSelf());
		mediator.tell(new Put(getSelf()), getSelf());
		this.cleanupTask = getContext()
				.system()
				.scheduler()
				.schedule(workTimeout.div(2), workTimeout.div(2), getSelf(),
						CleanupTick, getContext().dispatcher(), getSelf());
	}

	@Override
    public void preStart() {
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
            MemberEvent.class, UnreachableMember.class);
    }

	@Override
	public void postStop() {
		cleanupTask.cancel();
	}

	@Override
	public void onReceive(Object message) throws Exception {
		//log.info("message:" + message.toString());
		String sAddress = URLDecoder.decode(getSender().path().toString().replace(getSender().path().parent().toString() + "/", ""), "utf-8");
		Option<String> sHost = getContext().actorSelection(sAddress).anchorPath().address().host();
		Option<String> empty = scala.Option.apply(null);
		String sHostAddress = sHost == empty ? "" : sHost.get();

		if (message instanceof RegisterWorker) {
			InetAddress localhost = Inet4Address.getLocalHost();
			String loocalAddress = localhost.getHostAddress().toString();

			RegisterWorker msg = (RegisterWorker) message;
			String workerId = msg.workerId;
			if (workers.containsKey(workerId)) {
				workers.put(workerId,
						workers.get(workerId).copyWithRef(getSender()));
			} else {
				log.debug("Worker registered: {}", workerId);
				workers.put(workerId, new WorkerState(getSender(),
						Idle.instance));
				if (!pendingWork.isEmpty())
					getSender().tell(WorkIsReady.getInstance(), getSelf());
			}
		} else if (message instanceof WorkerRequestsWork) {
			WorkerRequestsWork msg = (WorkerRequestsWork) message;
			String workerId = msg.workerId;
			if (!pendingWork.isEmpty()) {
				WorkerState state = workers.get(workerId);
				if (state != null && state.status.isIdle()) {
					Work work = pendingWork.remove();
					log.debug("Giving worker {} some work {}", workerId, "");
					// TODO store in Eventsourced
					getSender().tell(work, getSelf());
					workers.put(workerId, state.copyWithStatus(new Busy(work,
							workTimeout.fromNow())));
				}
			}
		} else if (message instanceof WorkIsDone) {
			WorkIsDone msg = (WorkIsDone) message;
			String workerId = msg.workerId;
			String workId = msg.workId;
			WorkInfo currentWorkInfo = workProgress.get(workId);
			if (currentWorkInfo == null) {
				log.debug("Discarding WorkIsDone message because the"
						+ " associated WorkInfo object does not exist. The job"
						+ " probably failed on another worker.");
				// Acknowledge that the work has finished, even though we're
				// going to ignore it. Got to keep the workers happy.
				acknowledgeWorkCompletion(msg, workerId, workId);
				return;
			}
			currentWorkInfo.result = msg.result;
			workProgress.put(workId, currentWorkInfo);

			double totalArea = 0;
			double completedArea = 0;
			int completedWork = 0;
			List<WorkInfo> allTaskWorks = getAllTaskWork(currentWorkInfo.work.jobProgressId);
			int totalNoOfWork = allTaskWorks.size();
			for (WorkInfo w : allTaskWorks) {
				if (w.result != null) {
					completedArea += w.processedArea;
					completedWork++;
				}
				totalArea += w.area;
			}

			if (totalNoOfWork == completedWork) {
				//foldResults(currentWorkInfo);
				fold(currentWorkInfo);
				updateTaskProgress(currentWorkInfo.work.jobProgressId,
						completedArea, totalArea, TaskState.FINISHED, null);
				removeWork(currentWorkInfo.work.jobProgressId);
			}

			acknowledgeWorkCompletion(msg, workerId, workId);
		} else if (message instanceof WorkFailed) {
			WorkFailed msg = (WorkFailed) message;
			String workerId = msg.workerId;
			String workId = msg.workId;
			WorkerState state = workers.get(workerId);
			if (state != null && state.status.isBusy()
					&& state.status.getWork().workId.equals(workId)) {
				log.info("Work failed: {}", state.status.getWork());
				// TODO store in Eventsourced
				workers.put(workerId, state.copyWithStatus(Idle.instance));
				pendingWork.add(state.status.getWork());
				notifyWorkers();
			}
		} else if (message instanceof ProgressCheckPoint) {
			ProgressCheckPoint check = (ProgressCheckPoint) message;
			WorkInfo work = workProgress.get(check.workId);
			if (work == null) {
				log.debug("Discarding ProgressCheckPoint message because the"
						+ " associated WorkInfo object does not exist. The job"
						+ " probably failed on another worker.");
				return;
			}
			work.processedArea = work.area * check.progress.getFraction();
			String taskId = work.work.jobProgressId;
			List<WorkInfo> allTaskWork = getAllTaskWork(taskId);
			double totalArea = 0;
			double completedArea = 0;
			for (WorkInfo wi : allTaskWork) {
				totalArea += wi.area;
				completedArea += wi.processedArea;
			}
			updateTaskProgress(taskId, completedArea, totalArea,
					TaskState.RUNNING, null);
		} else if (message instanceof Work) {
			Work work = (Work) message;
			// idempotent
			if (workIds.contains(work.workId)) {
				getSender().tell(new Ack(work.workId), getSelf());
			} else {
				log.debug("Accepted work: {}", work);
				// TODO store in Eventsourced
				pendingWork.add(work);
				workIds.add(work.workId);
				WorkInfo workInfo = new WorkInfo(work, null);
				workProgress.put(work.workId, workInfo);
				getSender().tell(new Ack(work.workId), getSelf());
				notifyWorkers();
			}
		} else if (message instanceof Job.Error) {
			Job.Error error = (Job.Error) message;
			updateTaskProgress(error.work.jobProgressId,
					0, 0, TaskState.EXECUTION_ERROR, error.exception.getMessage());
			removeWork(error.work.jobProgressId);

			// On error, the worker transitions to its idle state - so update
			// the local copy of that state.
			WorkerState state = workers.get(error.workerId);
			if (state != null && state.status.isBusy()) {
				workers.put(error.workerId, state.copyWithStatus(Idle.instance));
			}
			System.out.println("Error:" + error.exception.getMessage());
		} else if (message == CleanupTick) {
			Iterator<Map.Entry<String, WorkerState>> iterator = workers
					.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, WorkerState> entry = iterator.next();
				WorkerState state = entry.getValue();
				if (state.status.isBusy()) {
					if (state.status.getDeadLine().isOverdue()) {
						Work work = state.status.getWork();
						log.info("Work timed out: {}", work);
						// TODO store in Eventsourced
						iterator.remove();
						pendingWork.add(work);
						notifyWorkers();
					}
				}
			}
		} else {
			System.out.println("unhandled:" + message);
			unhandled(message);
		}
	}

	private void acknowledgeWorkCompletion(
			WorkIsDone msg, String workerId, String workId) {

		WorkerState state = workers.get(workerId);
		if (state != null && state.status.isBusy()
				&& state.status.getWork().workId.equals(workId)) {
			Work work = state.status.getWork();
			Object result = msg.result;
			log.debug("Work is done: {} => {} by worker {}", work, result,
					workerId);
			System.out.println("Work is done: " + work + " => " + result
					+ " by worker " + workerId);
			// TODO store in Eventsourced
			workers.put(workerId, state.copyWithStatus(Idle.instance));
			mediator.tell(new DistributedPubSubMediator.Publish(
					ResultsTopic, new WorkResult(workId, result)),
					getSelf());
			getSender().tell(new Ack(workId), getSelf());
		} else {
			if (workIds.contains(workId)) {
				// previous Ack was lost, confirm again that this is done
				getSender().tell(new Ack(workId), getSelf());
			}
		}
	}

	private void updateTaskProgress(String taskId, double completedArea,
			double totalArea, TaskState state, String errorMessage) {
		ActorSelection database = getContext().system().actorSelection(
				"akka://Workers/user/database");
		double fraction = completedArea / totalArea;
		JobUpdate update = new JobUpdate(taskId, fraction, state, errorMessage);
		database.tell(update, getSelf());
	}

	private List<WorkInfo> getAllTaskWork(String taskId) {
		List<WorkInfo> list = new ArrayList<>();
		for (WorkInfo wi : workProgress.values()) {
			if (wi.work.jobProgressId.equals(taskId))
				list.add(wi);
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	private void fold(WorkInfo currentWorkInfo) {
		ActorSelection database = getContext().system().actorSelection(
					"akka://Workers/user/database");
		List<WorkInfo> list = getAllTaskWork(currentWorkInfo.work.jobProgressId);
		List<String> results = new ArrayList<>();
		for (WorkInfo w : list) {
			results.add(w.result.toString());
		}

		Fold msg = new Fold(results, currentWorkInfo);
		database.tell(msg, getSelf());
	}

	private void removeWork(String jobProgressId) {
		// Remove jobs that are currently being worked on. This allows Master
		// to ignore future WorkCompleted messages for this job.
		Iterator<Entry<String, WorkInfo>> iter = workProgress.entrySet()
				.iterator();
		while (iter.hasNext()) {
			Entry<String, WorkInfo> entry = iter.next();
			if (entry.getValue().work.jobProgressId.equals(jobProgressId))
				iter.remove();
		}
		// Remove jobs that haven't started yet.
		Iterator<Work> iter2 = pendingWork.iterator();
		while (iter2.hasNext()) {
			Work work = iter2.next();
			if (work.jobProgressId.equals(jobProgressId))
				iter2.remove();
		}
	}

	private void notifyWorkers() {
		if (!pendingWork.isEmpty()) {
			// could pick a few random instead of all
			for (WorkerState state : workers.values()) {
				if (state.status.isIdle())
					state.ref.tell(WorkIsReady.getInstance(), getSelf());
			}
		}
	}

	private static abstract class WorkerStatus {
		protected abstract boolean isIdle();

		private boolean isBusy() {
			return !isIdle();
		};

		protected abstract Work getWork();

		protected abstract Deadline getDeadLine();
	}

	private static final class Idle extends WorkerStatus {
		private static final Idle instance = new Idle();

		public static Idle getInstance() {
			return instance;
		}

		@Override
		protected boolean isIdle() {
			return true;
		}

		@Override
		protected Work getWork() {
			throw new IllegalAccessError();
		}

		@Override
		protected Deadline getDeadLine() {
			throw new IllegalAccessError();
		}

		@Override
		public String toString() {
			return "Idle";
		}
	}

	private static final class Busy extends WorkerStatus {
		private final Work work;
		private final Deadline deadline;

		private Busy(Work work, Deadline deadline) {
			this.work = work;
			this.deadline = deadline;
		}

		@Override
		protected boolean isIdle() {
			return false;
		}

		@Override
		protected Work getWork() {
			return work;
		}

		@Override
		protected Deadline getDeadLine() {
			return deadline;
		}

		@Override
		public String toString() {
			return "Busy{" + "work=" + work + ", deadline=" + deadline + '}';
		}
	}





	private static final class WorkerState {
		public final ActorRef ref;
		public final WorkerStatus status;

		private WorkerState(ActorRef ref, WorkerStatus status) {
			this.ref = ref;
			this.status = status;
		}

		private WorkerState copyWithRef(ActorRef ref) {
			return new WorkerState(ref, this.status);
		}

		private WorkerState copyWithStatus(WorkerStatus status) {
			return new WorkerState(this.ref, status);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			WorkerState that = (WorkerState) o;

			if (!ref.equals(that.ref))
				return false;
			if (!status.equals(that.status))
				return false;

			return true;
		}

		@Override
		public int hashCode() {
			int result = ref.hashCode();
			result = 31 * result + status.hashCode();
			return result;
		}

		@Override
		public String toString() {
			return "WorkerState{" + "ref=" + ref + ", status=" + status + '}';
		}
	}

	private static final Object CleanupTick = new Object() {
		@Override
		public String toString() {
			return "CleanupTick";
		}
	};
	// TODO cleanup old workers
	// TODO cleanup old workIds

}
