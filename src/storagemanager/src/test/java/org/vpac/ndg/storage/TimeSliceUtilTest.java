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

package org.vpac.ndg.storage;

import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.vpac.ndg.common.datamodel.CellSize;
import org.vpac.ndg.storage.dao.DatasetDao;
import org.vpac.ndg.storage.model.Dataset;
import org.vpac.ndg.storage.model.TimeSlice;
import org.vpac.ndg.storage.util.TimeSliceUtil;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"/spring/config/TestBeanLocations.xml"})
public class TimeSliceUtilTest extends AbstractJUnit4SpringContextTests {

	@Autowired
	DatasetDao datasetDao;
	@Autowired
	TimeSliceUtil timeSliceUtil;


	@Test
	public void testGetLocation() {
		String testTimeSliceUtilString = "testTimeSliceUtil";
		Dataset ds = datasetDao.findDatasetByName(
			testTimeSliceUtilString, CellSize.km10);
		if (ds == null) {
			ds = new Dataset();
			ds.setName(testTimeSliceUtilString);
			ds.setResolution(CellSize.km10);
			datasetDao.create(ds);
		}
		TimeSlice ts = new TimeSlice(new Date());
		datasetDao.addTimeSlice(ds.getId(), ts);
		assertTrue(timeSliceUtil.getFileLocation(ts).toString().contains(testTimeSliceUtilString));
	}
}
