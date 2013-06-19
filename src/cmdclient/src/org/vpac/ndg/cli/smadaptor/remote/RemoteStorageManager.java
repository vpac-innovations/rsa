package org.vpac.ndg.cli.smadaptor.remote;

import org.springframework.context.ApplicationContext;
import org.vpac.ndg.cli.smadaptor.BandConnector;
import org.vpac.ndg.cli.smadaptor.DataCleanup;
import org.vpac.ndg.cli.smadaptor.DataDownloader;
import org.vpac.ndg.cli.smadaptor.DataExport;
import org.vpac.ndg.cli.smadaptor.DataImport;
import org.vpac.ndg.cli.smadaptor.DataQuery;
import org.vpac.ndg.cli.smadaptor.DataUpload;
import org.vpac.ndg.cli.smadaptor.DatasetConnector;
import org.vpac.ndg.cli.smadaptor.StorageManager;
import org.vpac.ndg.cli.smadaptor.TaskConnector;
import org.vpac.ndg.cli.smadaptor.TimesliceConnector;

public class RemoteStorageManager implements StorageManager {
	private ApplicationContext appContext;
	private String baseUri;
	
	public RemoteStorageManager(String baseUri, ApplicationContext appContext) {
		this.appContext = appContext;
		this.baseUri = baseUri;
	}
	
	@Override
	public DatasetConnector getDatasetConnector() {
		RemoteDatasetConnector dc = (RemoteDatasetConnector)appContext.getBean("datasetConnector");
		dc.setBaseUri(this.baseUri);
		return dc;
	}

	@Override
	public DataUpload getDataUploader() {
		RemoteDataUpload dataUpload =  (RemoteDataUpload) appContext.getBean("dataUpload");
		dataUpload.setBaseUri(this.baseUri);
		return dataUpload;
	}

	@Override
	public DataImport getDataImporter() {
		RemoteDataImport dataImport = (RemoteDataImport) appContext.getBean("dataImport");
		dataImport.setBaseUri(this.baseUri);
		return dataImport;
	}

	@Override
	public DataExport getDataExporter() {
		RemoteDataExport dataExport = (RemoteDataExport) appContext.getBean("dataExport");
		dataExport.setBaseUri(this.baseUri);
		return dataExport;
	}

	@Override
	public DataQuery getDataQuery() {
		RemoteDataQuery remoteDataQuery = (RemoteDataQuery) appContext.getBean("dataQuery");
		remoteDataQuery.setBaseUri(baseUri);
		return remoteDataQuery;
	}

	@Override
	public DataCleanup getDataCleanup() {
		RemoteDataCleanup dc = (RemoteDataCleanup) appContext.getBean("dataCleanup");
		dc.setBaseUri(this.baseUri);
		return dc;
	}

	@Override
	public TimesliceConnector getTimesliceConnector() {
		RemoteTimesliceConnector timesliceConnector = (RemoteTimesliceConnector) appContext.getBean("timesliceConnector");
		timesliceConnector.setBaseUri(this.baseUri);
		return timesliceConnector;
	}

	@Override
	public BandConnector getBandConnector() {
		RemoteBandConnector bandConnector = (RemoteBandConnector) appContext.getBean("bandConnector");
		bandConnector.setBaseUri(this.baseUri);
		return bandConnector;
	}

	@Override
	public TaskConnector getTaskConnector() {
		RemoteTaskConnector taskConnector  = (RemoteTaskConnector) appContext.getBean("taskConnector");
		taskConnector.setBaseUri(this.baseUri);
		return taskConnector;
	}

	@Override
	public DataDownloader getDataDownloader() {
		RemoteDataDownloader downloader  = (RemoteDataDownloader) appContext.getBean("dataDownloader");
		downloader.setBaseUri(this.baseUri);
		return downloader;
	}
}