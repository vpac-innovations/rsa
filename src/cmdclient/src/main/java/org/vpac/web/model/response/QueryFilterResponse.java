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

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.vpac.ndg.query.FilterUtils;
import org.vpac.ndg.query.filter.Filter;

/**
 * This class is intended for a single query filter response.
 * 
 * @author hsumanto
 * 
 */
public class QueryFilterResponse extends QueryNodeResponse {

	public QueryFilterResponse() {
	}

	public QueryFilterResponse(Class<? extends Filter> f) {
		if (f == null)
			return;

		FilterUtils utils = new FilterUtils();
		setName(utils.getName(f));
		setDescription(utils.getDescription(f));
		setType("filter");
		setQualname(f.getCanonicalName());
		setInputs(new ArrayList<QueryInputResponse>());
		setOutputs(new ArrayList<QueryOutputResponse>());

		for (Field field : utils.getLiterals(f)) {
			getInputs().add(
					new QueryInputResponse(field.getName(), field.getType()
							.getSimpleName()));
		}
		for (Field field : utils.getSources(f)) {
			getInputs().add(
					new QueryInputResponse(field.getName(), field.getType()
							.getSimpleName()));
		}
		for (Field field : utils.getCells(f)) {
			getOutputs().add(
					new QueryOutputResponse(field.getName(), field.getType()
							.getSimpleName()));
		}
	}
}