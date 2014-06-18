package org.vpac.ndg.query.stats;

import java.io.Serializable;

import org.vpac.ndg.query.filter.Foldable;
import org.vpac.ndg.query.math.ScalarElement;

/**
 * A bucket in a histogram.
 * @author Alex Fraser
 */
public class Bucket implements Foldable<Bucket>, Serializable {

	private static final long serialVersionUID = 1L;
	private String id;	
	private double lower;
	private double upper;
	private Stats stats;

	public Bucket(Double lower, Double upper, Stats stats) {
		this.lower = lower;
		this.upper = upper;
		this.stats = stats;
	}
	public Bucket() {
	}

	public boolean canContain(ScalarElement value) {
		if (value.compareTo(lower) < 0)
			return false;
		else if (value.compareTo(upper) >= 0)
			return false;
		return true;
	}

	@Override
	public Bucket fold(Bucket other) {
		return new Bucket(Math.min(lower, other.lower),
				Math.max(upper, other.upper), stats.fold(other.stats));
	}

	public double getLower() {
		return lower;
	}

	public void setLower(double lower) {
		this.lower = lower;
	}

	public double getUpper() {
		return upper;
	}

	public void setUpper(double upper) {
		this.upper = upper;
	}

	public Stats getStats() {
		return stats;
	}

	public void setStats(Stats stats) {
		this.stats = stats;
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}	

	@Override
	public String toString() {
		return String.format("Bucket(%g-%g)", lower, upper);
	}
}
