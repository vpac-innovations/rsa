package org.vpac.ndg.cli.smadaptor.remote;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import org.vpac.ndg.cli.smadaptor.TimesliceConnector;
import org.vpac.ndg.storage.model.Dataset;
import org.vpac.ndg.storage.model.TimeSlice;
import org.vpac.ndg.storage.model.TimeSliceLock;
import org.vpac.web.model.response.DatasetResponse;
import org.vpac.web.model.response.TimeSliceCollectionResponse;
import org.vpac.web.model.response.TimeSliceLockCollectionResponse;
import org.vpac.web.model.response.TimeSliceResponse;

public class RemoteTimesliceConnector implements TimesliceConnector {
	public static String GET_ALL_TIMESLICE_BY_ID_URL = "/SpatialCubeService/TimeSlice/{timesliceId}.xml";
	public static String GET_ALL_TIMESLICE_BY_DATASET_ID_URL = "/SpatialCubeService/TimeSlice.xml?datasetId={datasetId}";
	public static String GET_DATASET_BY_ID_URL = "/SpatialCubeService/Dataset/{id}.xml";
	public static String GET_DATASET_BY_NAME_RESOLUTION_URL = "/SpatialCubeService/Dataset/Search.xml?name={name}&resolution={resolution}";
	public static String DELETE_TIMESLICE_BY_ID_URL = "/SpatialCubeService/TimeSlice/Delete/{id}.xml";
	public static String CREATE_OR_UPDATE_TIMESLICE_URL = "/SpatialCubeService/TimeSlice.xml?timesliceId={id}&datasetId={datasetId}&created={created}&abs={abs}&xmin={xmin}&xmax={xmax}&ymin={ymin}&ymax={ymax}";
	public static String GET_DATASET_BY_TIMESLICE_ID = "/SpatialCubeService/TimeSlice/Parent/{id}.xml";
	public static String GET_ALL_TIMESLICELOCK_BY_ID_URL = "/SpatialCubeService/TimeSlice/Lock/{id}.xml";
	
	private String baseUri;
	
	public String getBaseUri() {
		return baseUri;
	}

	public void setBaseUri(String baseUri) {
		this.baseUri = baseUri;
	}
	
	@Autowired
	protected RestTemplate restTemplate;
	
	
	@Override
	public TimeSlice get(String timesliceId) {
		TimeSliceResponse response = restTemplate.getForObject(baseUri + GET_ALL_TIMESLICE_BY_ID_URL, TimeSliceResponse.class, timesliceId);
		return response.toTimeSlice();
	}

	@Override
	public List<TimeSliceLock> listLocks(String timesliceId) {
		TimeSliceLockCollectionResponse response = restTemplate.getForObject(baseUri + GET_ALL_TIMESLICELOCK_BY_ID_URL, TimeSliceLockCollectionResponse.class, timesliceId);
		return response.toTimeSliceLockList();
	}

	@Override
	public List<TimeSlice> list(String datasetId) {
		TimeSliceCollectionResponse response = restTemplate.getForObject(baseUri + GET_ALL_TIMESLICE_BY_DATASET_ID_URL, TimeSliceCollectionResponse.class, datasetId);
		return response.toTimeSliceList();
	}

	@Override
	public TimeSlice create(String datasetId, String created, String abs) {
		TimeSliceResponse response = restTemplate.postForObject(baseUri + CREATE_OR_UPDATE_TIMESLICE_URL, null, TimeSliceResponse.class, null, datasetId, created, abs, null, null, null, null);
		return response.toTimeSlice();
	}

	@Override
	public void delete(String id) throws IOException {
		TimeSliceResponse response = restTemplate.postForObject(baseUri + DELETE_TIMESLICE_BY_ID_URL, null, TimeSliceResponse.class, id);
		System.out.println("The dataset id:" + response.getId() + "is deleted");
	}

	@Override
	public Dataset getDataset(String id) {
		DatasetResponse response = restTemplate.getForObject(baseUri + GET_DATASET_BY_TIMESLICE_ID, DatasetResponse.class, id);
		return response.toDataset();
	}

	@Override
	public String getLocation(String timesliceId) {
/*		TimeSlice ts = get(timesliceId);
		return timeSliceUtil.getFileLocation(ts).toString();
*/	
		return null;
	}

	@Override
	public void update(TimeSlice newTs) throws IOException {
		restTemplate.postForObject(baseUri + CREATE_OR_UPDATE_TIMESLICE_URL, null, TimeSliceResponse.class, newTs.getId(), null, newTs.getCreated(), newTs.getDataAbstract(), newTs.getXmin(), newTs.getXmax(), newTs.getYmin(), newTs.getYmax());
	}

	@Override
	public void updateInfo(TimeSlice newTs) {
		restTemplate.postForObject(baseUri + CREATE_OR_UPDATE_TIMESLICE_URL, null, TimeSliceResponse.class, newTs.getId(), null, newTs.getCreated(), newTs.getDataAbstract(), newTs.getXmin(), newTs.getXmax(), newTs.getYmin(), newTs.getYmax());
	}
}