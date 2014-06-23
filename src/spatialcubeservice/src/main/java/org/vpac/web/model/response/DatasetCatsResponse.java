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

package org.vpac.web.model.response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.vpac.ndg.common.datamodel.CellSize;
import org.vpac.ndg.query.stats.Bucket;
import org.vpac.ndg.query.stats.Hist;
import org.vpac.ndg.query.stats.Stats;
import org.vpac.ndg.storage.model.Dataset;
import org.vpac.ndg.storage.model.DatasetCats;

@XmlRootElement(name = "DatasetCats")
public class DatasetCatsResponse {
	private String id;
	private String datasetId;
	private String timeSliceId;
	private String bandId;
	private String tableType;
	private String categorisation;
	private DatasetCats cat;
	private Dataset dataset;
	private List<CatsElement> table;
	
	public String getId() {
		return id;
	}
	@XmlAttribute
	public void setId(String id) {
		this.id = id;
	}

	public String getDatasetId() {
		return datasetId;
	}
	@XmlAttribute
	public void setDatasetId(String datasetId) {
		this.datasetId = datasetId;
	}
	public String getTimeSliceId() {
		return timeSliceId;
	}
	@XmlAttribute
	public void setTimeSliceId(String timeSliceId) {
		this.timeSliceId = timeSliceId;
	}
	public String getBandId() {
		return bandId;
	}
	@XmlAttribute
	public void setBandId(String bandId) {
		this.bandId = bandId;
	}
	public String getCategorisation() {
		return categorisation;
	}
	@XmlAttribute
	public void setCategorisation(String categorisation) {
		this.categorisation = categorisation;
	}
	public List<CatsElement> getRows() {
		return table;
	}
	@XmlAttribute
	public void setRows(List<CatsElement> table) {
		this.table = table;
	}
	
	public DatasetCatsResponse() {
	}
	
	public DatasetCatsResponse(DatasetCats cat, Dataset ds) {
		this.setId(cat.getId());
		this.setDatasetId(cat.getDatasetId());
		this.setBandId(cat.getBandId());
		this.setCategorisation(cat.getName());
		this.setTableType("categories");
		this.dataset = ds;
		this.cat = cat;
	}
	
	public void processSummary(Double lower, Double upper) {
		CellSize outputResolution = dataset.getResolution();
		List<CatsElement> result = new ArrayList<CatsElement>();
		double cellArea = outputResolution.toDouble() * outputResolution.toDouble();
		for (Entry<Integer, Hist> entry : this.cat.getCats().getCategories().entrySet()) {
			Stats s = new Stats();
			for (Bucket b : entry.getValue().getBuckets()) {
				if (lower != null && b.getLower() < lower)
					continue;
				if (upper != null && b.getUpper() > upper)
					continue;
				s = s.fold(b.getStats());
			}
			if (s.getCount() > 0)
				result.add(new CatsElement(entry.getKey(), s.getCount() * cellArea));
		}
		this.setRows(result);
	}
	public String getTableType() {
		return tableType;
	}
	public void setTableType(String tableType) {
		this.tableType = tableType;
	}
}