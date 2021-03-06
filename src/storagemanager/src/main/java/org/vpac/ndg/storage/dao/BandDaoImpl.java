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

package org.vpac.ndg.storage.dao;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.vpac.ndg.storage.model.Band;
import org.vpac.ndg.storage.model.Dataset;
import org.vpac.ndg.storage.util.CustomHibernateDaoSupport;

public class BandDaoImpl extends CustomHibernateDaoSupport implements BandDao {
	public BandDaoImpl() {
	}

	@Transactional
	@Override
	public Band create(Band b) {
		throw new UnsupportedOperationException("Use DatasetDao");
	}

	@Transactional
	@Override
	public void update(Band b) {
		getSession().update(b);
	}

	@Transactional
	@Override
	public void delete(Band b) {
		getSession().delete(b);
	}

	@Transactional
	@Override
	public Band retrieve(String id) {
		return (Band) getSession().get(Band.class, id);
	}

	@Transactional
	@Override
	public Band find(String datasetId, String bandName) {
		@SuppressWarnings("unchecked")
		List<Band> list = getSession()
			.createQuery("SELECT b FROM Dataset d join d.bands b WHERE d.id=? AND b.name=?")
			.setString(0, datasetId)
			.setString(1, bandName)
			.list();
		if(list.size() <= 0)
			return null;
		return list.get(0);
	}

	@Transactional
	@Override
	public Dataset getParentDataset(String bandId) {
		@SuppressWarnings("unchecked")
		List<Dataset> list = getSession()
			.createQuery("SELECT d FROM Dataset d join d.bands b WHERE b.id=?")
			.setString(0, bandId)
			.list();
		if(list.size() <= 0)
			return null;
		return list.get(0);
	}

}
