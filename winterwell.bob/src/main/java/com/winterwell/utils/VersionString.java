package com.winterwell.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * See https://semver.org/
 * @author daniel
 * @testedby VersionStringTest TODO
 */
public final class VersionString implements Comparable<VersionString> 
{

	/**
	 * each bit is Integer|String
	 */
	private final List bits;
	private final String version;

	/**
	 * 
	 * @param version e.g. "2.1.3"
	 */
	public VersionString(String version) {
		this.version = version;
		String[] sbits = version.split("[\\._ ]");
		bits = new ArrayList();
		for (int i = 0; i < sbits.length; i++) {
			if (StrUtils.isInteger(sbits[i])) {
				bits.add(Integer.valueOf(sbits[i]));
			} else {
				bits.add(sbits[i]);
			}
		}
	}

	/**
	 * true if this is greater-than-or-equals to b
	 * @param b
	 * @return
	 */
	public boolean geq(String b) {
		VersionString vsb = new VersionString(b);
		return geq(vsb);
	}

	
	/**
	 * true if this is greater-than-or-equals to b
	 * @param b
	 * @return
	 */
	public boolean geq(VersionString vsb) {
		int c = compareTo(vsb);
		return c >= 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VersionString other = (VersionString) obj;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "v"+version;
	}

	public boolean isHigher(VersionString vsb) {
		return compareTo(vsb) > 0;
	}

	@Override
	public int compareTo(VersionString vsb) {
		if (equals(vsb)) {
			return 0;
		}
		for(int i=0; i<bits.size(); i++) {
			Object abi = bits.get(i);
			if (vsb.bits.size() <= i) {
				if (abi instanceof Integer) {
					return -1;	
				}
				return 0;
			}
			Object bbi = vsb.bits.get(i);
			if (abi instanceof Integer) {
				if (bbi instanceof Integer) {
					int abin = (Integer) abi;
					int bbin = (Integer) bbi;
					if (abin==bbin) continue;
					return abin - bbin;
				} else {
					return -1;
				}
			}
			if (bbi instanceof Integer) {
				return 1;
			}
		}
		return 0;
	}

	public String getMajorVersion() {
		// NB: bits are Integer or String
		return String.valueOf(bits.get(0));
	}

}
