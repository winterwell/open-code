package com.winterwell.datalog.server;

import com.winterwell.datalog.CSV;
import com.winterwell.web.app.CrudServlet;

public class CsvServlet extends CrudServlet<CSV>  {

	public CsvServlet() {
		super(CSV.class);
	}	

}
