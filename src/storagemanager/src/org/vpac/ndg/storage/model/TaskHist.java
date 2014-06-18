package org.vpac.ndg.storage.model;

import java.io.Serializable;

import org.vpac.ndg.query.stats.Hist;

/**
 * Groups values into arbitrary buckets (categories to be provided by user). 
 * @author Jin Park
 */
public class TaskHist implements Serializable {

	private static final long serialVersionUID = 1L;
	private String id;
	private String taskId;
	private String name;
	private Hist hist;
	
	public TaskHist(String taskId, String name, Hist hist) {
		this.taskId = taskId;
		this.name = name;
		this.hist = hist;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public Hist getHist() {
		return hist;
	}

	public void setHist(Hist hist) {
		this.hist = hist;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
