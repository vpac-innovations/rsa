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

import java.io.IOException;

import org.vpac.ndg.query.filter.CellType;
import org.vpac.ndg.query.filter.Description;
import org.vpac.ndg.query.filter.Filter;
import org.vpac.ndg.query.filter.InheritDimensions;
import org.vpac.ndg.query.filter.Rank;
import org.vpac.ndg.query.iteration.Reduction;
import org.vpac.ndg.query.math.BoxReal;
import org.vpac.ndg.query.math.Element;
import org.vpac.ndg.query.math.VectorReal;
import org.vpac.ndg.query.sampling.Cell;
import org.vpac.ndg.query.sampling.PixelSource;

/**
 * Sum values over time
 *
 * @author Alex Fraser
 */
@Description(name = "Sum", description = "Sum values over time")
@InheritDimensions(from = "input", reduceBy = 1)
public class SumOverTime implements Filter {

	@Rank(lowerBound = 2)
	public PixelSource input;

	@CellType("input")
	public Cell output;

	Reduction reduction;
	VectorReal tco = VectorReal.createEmpty(1);
	Element<?> val;

	@Override
	public void initialise(BoxReal bounds) throws QueryException {
		reduction = new Reduction(input.getBounds());
		val = input.getPrototype().getElement().copy();
	}

	@Override
	public void kernel(VectorReal coords) throws IOException {
		// Search over all times.
		boolean valid = false;
		val.set(0);
		for (VectorReal varcoords : reduction.getIterator(coords)) {
			Element<?> ival = input.getPixel(varcoords);
			if (!ival.isValid())
				continue;
			val.add(ival);
			valid = true;
		}

		if (valid)
			output.set(val);
		else
			output.unset();
	}
}
