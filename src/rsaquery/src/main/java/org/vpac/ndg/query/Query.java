/*
 * This file is part of the Raster Storage Archive (RSA).
 *
 * The RSA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * The RSA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * the RSA.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2013 CRCSI - Cooperative Research Centre for Spatial Information
 * http://www.crcsi.com.au/
 */

package org.vpac.ndg.query;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vpac.ndg.query.DatasetOutput.VariableBindingDefinition;
import org.vpac.ndg.query.QueryDefinition.DatasetInputDefinition;
import org.vpac.ndg.query.QueryDefinition.GridDefinition;
import org.vpac.ndg.query.coordinates.CoordinateUtils;
import org.vpac.ndg.query.coordinates.GridProjected;
import org.vpac.ndg.query.coordinates.QueryCoordinateSystem;
import org.vpac.ndg.query.coordinates.TimeAxis;
import org.vpac.ndg.query.coordinates.Warp;
import org.vpac.ndg.query.coordinates.WarpFactory;
import org.vpac.ndg.query.filter.Accumulator;
import org.vpac.ndg.query.filter.Foldable;
import org.vpac.ndg.query.iteration.Flatten;
import org.vpac.ndg.query.iteration.ZipN;
import org.vpac.ndg.query.math.BoxInt;
import org.vpac.ndg.query.math.BoxReal;
import org.vpac.ndg.query.math.VectorInt;
import org.vpac.ndg.query.sampling.Binding;
import org.vpac.ndg.query.sampling.PageCache;
import org.vpac.ndg.query.sampling.TileGenerator;
import org.vpac.ndg.query.sampling.TilingStrategy;
import org.vpac.ndg.query.sampling.TilingStrategyStride;

import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.dataset.NetcdfDataset;

/**
 * Generates new datasets by passing existing data through a filter. In
 * programming terms, a {@link FilterAdapter} is the operation and the Query provides
 * the parameters.
 *
 * @author Alex Fraser
 * @see FilterAdapter
 * @see QueryDefinition
 */
public class Query implements Closeable {

	final Logger log = LoggerFactory.getLogger(Query.class);

	protected String referential;
	protected DatasetStore datasetStore;
	protected CoordinateUtils coordinateUtils;
	protected BindingStore bindings;
	protected DatasetOutput outputDs;
	protected NetcdfFileWriter output;
	protected List<List<FilterAdapter>> filters;

	/**
	 * The arrangement of output pages. For input pages, see {@link PageCache}.
	 */
	protected TilingStrategy tilingStrategy;
	protected TileProcessor tileProcessor;
	protected Progress progress;
	protected int numThreads = 2;

	static final int TILE_SIZE = 256;
	static final int DEFAULT_WORKER_THREADS = 1;

	static {
		// The query engine has its own handling of nodata values; no need to
		// convert to NaN.
		NetcdfDataset.setUseNaNs(false);
	}

	/**
	 * Creates a null query. Use {@link #setMemento(QueryDefinition, String)} to
	 * initialise.
	 */
	public Query(NetcdfFileWriter output) {
		referential = null;
		bindings = new BindingStore();
		this.output = output;
		filters = new ArrayList<List<FilterAdapter>>();
		numThreads = DEFAULT_WORKER_THREADS;
		progress = new ProgressNull();

		//tilingStrategy = new TilingStrategyCube(TILE_SIZE);
		tilingStrategy = new TilingStrategyStride(TILE_SIZE * TILE_SIZE * 3);
	}

	public void setNumThreads(int numThreads) throws QueryException {
		if (numThreads < 1) {
			throw new QueryException(
					"Number of threads must be one or more.");
		}
		this.numThreads = numThreads;
	}

	/**
	 * Constructs this query from a {@link QueryDefinition}.
	 * @param qd The query, in serialised form.
	 * @param uri The specified uri.
	 */
	public void setMemento(QueryDefinition qdOrig, String uri) throws IOException,
			SecurityException, QueryException {

		log.info("Configuring query based on memento");

		referential = uri;

		datasetStore = new DatasetStore();
		coordinateUtils = new CoordinateUtils(datasetStore);

		// Pre-process query: Sort filters into creation order, expand
		// references to sockets, etc.
		QueryDefinitionPreprocessor qdp = new QueryDefinitionPreprocessor();
		qdp.setQueryDefinition(qdOrig);
		qdp.gatherFilterInfo();
		// Sort filters first - avoids sorting based on more numerous expanded
		// references.
		qdp.sortFilters();

		// Before socket refs can be expanded, the inputs need to be queried.
		log.info("Initialising inputs");
		initialiseInputs(qdp.getQueryDefinition());
		qdp.guessGrid(datasetStore.inputDatasets);
		qdp.expandReferences(datasetStore);

		// Now that references have been expanded, input bands can be marked as
		// required. These bands will be opened later; others will be ignored
		// if the DatasetProvider supports selective opening.
		log.info("Requesting bands {}", qdp.getInputVariableReferences());
		for (NodeReference nr : qdp.getInputVariableReferences())
			datasetStore.requestInputBand(nr);

		// Determine bounds now, before inputs have been fully determined.
		// This is an optimisation for opening a small section of a very large
		// tiled dataset.
		log.info("Initialising grid");
		GridProjected outputGrid = coordinateUtils.initialiseGrid(
				qdp.getQueryDefinition().output.grid);
		DateTime temporalBounds[] = determineTemporalBounds(
				qdp.getQueryDefinition());

		log.info("Opening inputs");
		openInputs(qdp.getQueryDefinition(), outputGrid, temporalBounds);

		// Now that the datasets are open, finish constructing the coordinate
		// system.
		QueryCoordinateSystem csys = constructCoordinateSystem(outputGrid);

		// Create one factory per thread. This ensures each filter is only
		// connected to others that were created for the same thread context.
		List<FilterFactory> factories = new ArrayList<FilterFactory>(numThreads);
		if (numThreads < 1) {
			throw new QueryException(
					"Invalid number of threads: must be greater than zero.");
		}
		for (int i = 0; i < numThreads; i++) {
			factories.add(new FilterFactory(datasetStore, bindings, csys));
		}

		// Contstruct filters. Doing this now allows the output dataset to
		// inherit metadata.
		log.info("Constructing filters");
		for (FilterFactory factory : factories) {
			filters.add(factory.createFilters(qdp.getQueryDefinition().filters));
		}

		// Construct the output, so the filters can be bound to it. At this
		// point a single filter store is passed in; when binding, all stores
		// are used (below).
		log.info("Creating output file");
		outputDs = new DatasetOutput(qdp.getQueryDefinition().output, output,
				datasetStore);
		List<VariableBindingDefinition> variableBindingDefs = outputDs
				.configure(csys, qdp.getQueryDefinition(), factories.get(0).getFilterStore());
		datasetStore.addDataset(outputDs);

		log.info("Binding filters to output");
		for (FilterFactory factory : factories) {
			factory.bindFilters(variableBindingDefs);
		}
		for (FilterAdapter f : new Flatten<FilterAdapter>(filters)) {
			f.verifyConfiguration();
		}
	}

	/**
	 * Start creating input datasets. They will be queried for their ideal
	 * bounds, and placed in the dataset store.
	 */
	private void initialiseInputs(QueryDefinition qd) throws IOException,
			QueryException {
		if (qd.inputs == null || qd.inputs.size() == 0) {
			throw new QueryBindingException("No inputs specified.");
		}
		for (DatasetInputDefinition did : qd.inputs) {
			DatasetInput di = new DatasetInput(did, referential, qd.cache);
			di.peekGrid();
			datasetStore.addDataset(di);
		}
	}

	private DateTime[] determineTemporalBounds(QueryDefinition qd) {
		DateTime temporalBounds[] = new DateTime[2];
		DateTimeFormatter fmt = ISODateTimeFormat.dateOptionalTimeParser().withZoneUTC();
		GridDefinition gd = qd.output.grid;
		if (gd.timeMin != null && !gd.timeMin.isEmpty())
			temporalBounds[0] = fmt.parseDateTime(qd.output.grid.timeMin);
		if (gd.timeMax != null && !gd.timeMax.isEmpty())
			temporalBounds[1] = fmt.parseDateTime(qd.output.grid.timeMax);
		return temporalBounds;
	}

	private void openInputs(QueryDefinition qd, GridProjected outputGrid, DateTime[] temporalBounds)
			throws IOException, QueryException {

		// STEP 2: Open input datasets fully, using output grid as a hint about
		// what to open. Actual bounds are determined here.

		// TODO: It might be possible to do a bit more of the output dataset
		// and filter configuration before this. That could allow for input
		// bounds to be guessed for non-gridded output datasets, e.g. those with
		// only a time dimension.

		WarpFactory warpFactory = new WarpFactory();
		BoxReal outputBounds = outputGrid.getBounds();
		for (DatasetInput di : datasetStore.getInputDatasets()) {
			// Warp the output bounds to the input coordinate space.
			Warp toLocal = warpFactory.createSpatialWarp(
					outputGrid.getSrs().getProjection(),
					di.getGrid().getSrs().getProjection());
			BoxReal localInputBounds = outputBounds.copy();
			toLocal.warp(localInputBounds);

			// Shrink the bounding box so it doesn't extend beyond the natural
			// extents of the input data. This has no effect on the output
			// bounds.
			localInputBounds.intersect(di.getGrid().getBounds());

			di.open(localInputBounds, temporalBounds[0], temporalBounds[1]);
		}
	}

	private QueryCoordinateSystem constructCoordinateSystem(
			GridProjected outputGrid) throws QueryException {
		TimeAxis timeAxis = coordinateUtils.collateTime(datasetStore.getInputDatasets());
		QueryCoordinateSystem csys = new QueryCoordinateSystem(outputGrid, timeAxis);
		return csys;
	}


	/**
	 * Run the configured filters over the input datasets.
	 */
	public void run() throws IOException, QueryException {

		progress.setNsteps(bindings.keys().size());

		log.info("Initialising filters");
		for (FilterAdapter f : new Flatten<FilterAdapter>(filters)) {
			f.initialise();
		}

		// Calculate volume (just for progress information)
		long totalPixels = 0;
		for (VectorInt shape : bindings.keys()) {
			totalPixels += shape.volume() * bindings.get(shape).size();
		}
		progress.setTotalQuanta(totalPixels);
		log.info("Total output volume: {} pixels", totalPixels);

		if (numThreads == 1)
			tileProcessor = new TileProcessorSingle();
		else
			tileProcessor = new TileProcessorMultiple(numThreads);

		log.info("Processing");
		int step = 0;
		try {
			for (VectorInt shape : bindings.keys()) {
				progress.setStep(step + 1, String.format(
						"Processing variables with shape %s", shape));

				process(shape, bindings.get(shape));
				step++;
			}
		} finally {
			tileProcessor.shutDown();
		}

		log.info("Finished");
		progress.setStep(step, "Finished running filters");
		progress.finished();
	}

	protected void process(VectorInt imageShape,
			Collection<Binding> localBindings)
			throws QueryException, IOException {

		// Rasterise output into a set of tiles.
		VectorInt tileShape = tilingStrategy.getTileShape(imageShape);
		VectorInt tileGridShape = tilingStrategy.getGridShape(imageShape);

		log.info("Image shape is {}", imageShape);
		log.debug("Tile grid shape is {}", tileGridShape);

		tileProcessor.setBindings(localBindings);

		// Iterate over the output tiles. Note that the input is tiled too, but
		// that is taken care of in PageCache.
		for (VectorInt tile : new TileGenerator(tileGridShape)) {
			VectorInt offset = tile.mulNew(tileShape);
			VectorInt end = offset.addNew(tileShape).min(imageShape);
			BoxInt bounds = new BoxInt(offset, end);

			log.trace("Processing tile {} with bounds {}", tile, bounds);

			// 1. Create arrays.
			for (Binding b : localBindings)
				b.setBounds(bounds);

			// 2. Run filters.
			tileProcessor.setBounds(bounds);
			tileProcessor.processTile();

			// 3. Write data.
			for (Binding b : localBindings)
				b.commit(output);

			long npixels = bounds.getSize().volume() * localBindings.size();
			progress.addProcessedQuanta(npixels);
		}

		progress.finishedStep();
	}

	/**
	 * Collects the output that was accumated while running this query.
	 *
	 * Most filters are designed to process pixels in an image and transform
	 * their values, with the new value being written to an output image. Some
	 * filters may also collect aggregate information about the data as it is
	 * being processed; e.g. a filter may sum the values of all the pixels it
	 * processes. This method gives access to that data.
	 *
	 * @return The output that has been accumulated while running this query.
	 *         This is a map from filter ID to accumulated output.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Map<String, Foldable<?>> getAccumulatedOutput() {
		Map<String, Foldable<?>> values = new HashMap<String, Foldable<?>>();

		// In a multithreaded query, accumulated output may be split across
		// several filter instances. So we zip all instances of the same filter
		// together, and then fold (reduce) the output.
		for (Iterable<FilterAdapter> fs : new ZipN<FilterAdapter>(filters)) {
			Foldable value = null;
			String id = null;
			for (FilterAdapter fa : fs) {
				if (!(fa.getInnerFilter() instanceof Accumulator<?>))
					continue;
				Accumulator ac = (Accumulator) fa.getInnerFilter();

				Foldable currentValue = ac.getAccumulatedOutput();
				if (value == null) {
					value = currentValue;
					id = fa.getName();
				} else {
					value = value.fold(value.getClass().cast(currentValue));
				}
				log.debug("Partial output of '{}' is {}", id, currentValue);
			}
			if (value != null) {
				log.info("Accumulated output of '{}' is {}", id, value);
				values.put(id, value);
			}
		}

		return values;
	}

	/**
	 * Releases all resources (except things that the garbage collector handles).
	 */
	@Override
	public void close() throws IOException {
		datasetStore.closeAll();
		for (FilterAdapter f : new Flatten<FilterAdapter>(filters))
			f.diagnostics();
	}

	public Progress getProgress() {
		return progress;
	}

	public void setProgress(Progress progress) {
		this.progress = progress;
	}


}
