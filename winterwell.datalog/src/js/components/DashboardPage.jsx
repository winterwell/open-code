import React from 'react';
import Login from '../base/youagain';
import _, { flatten } from 'lodash';
import pivot from 'data-pivot';

// import C from '../C';
import ServerIO from '../plumbing/ServerIO';
import DataStore from '../base/plumbing/DataStore';
// import ChartWidget from './ChartWidget';
import Misc from '../base/components/Misc';
import {Tabs, Tab} from '../base/components/Tabs';
import NewChartWidget, {timeSeriesChartFromKeyValue} from '../base/components/NewChartWidget';
import ChartWidget from '../base/components/ChartWidget';
import SearchQuery from '../base/searchquery';
import datalog, { getDataLogData } from '../base/plumbing/DataLog';
import { modifyPage } from '../base/plumbing/glrouter';
import PropControl from '../base/components/PropControl';
import PropControlPeriod from '../base/components/propcontrols/PropControlPeriod';
import SimpleTable, { Column } from '../base/components/SimpleTable';
import { asNum, uniq } from '../base/utils/miscutils';
import { isoDate } from '../base/utils/date-utils';

function DashboardPage() {

	let user = Login.getUser();
	if (!user) {
		return (
			<div className="page DashboardPage">
				<h2>My Dashboard</h2>
				<a href='#' onClick={(e) => DataStore.setShow('LoginWidget', true)}>Login or register</a>.
			</div>
		);
	}
	if ( ! Roles.isDev()) {
		return <h4>This tool is for Good-Loop staff</h4>;
	}

	let dataspace = DataStore.getUrlValue('d');
	if (!dataspace) {
		dataspace = 'gl';
		modifyPage(null, { d: dataspace });
	}
	let breakdown = DataStore.getUrlValue('breakdown');
	if (!breakdown) {
		breakdown = 'time';
		modifyPage(null, { breakdown });
	}
	let breakdowns = [breakdown];
	const query = DataStore.getUrlValue('q');
	let prob = DataStore.getUrlValue('prob');
	let filters = { dataspace, query, prob };
	let start = DataStore.getUrlValue('start');
	let end = DataStore.getUrlValue('end');
	let incs = DataStore.getUrlValue('incs');
	let ince = DataStore.getUrlValue('ince');
	let interval = DataStore.getUrlValue('interval');
	let op = DataStore.getUrlValue('op');
	let size = DataStore.getUrlValue('size');

	let dspec = window.location+""; //JSON.stringify(filters) + " " + JSON.stringify(breakdowns);
	const dpath = ['widget', 'Dashboard', dspec];
	let pvMydata = query && DataStore.fetch(dpath, () => {
		// Where does ad activity data go??
		// dataspaces: trk for evt.pxl, goodloop for evt.viewable, adview, click, close, open, opened
		// see https://github.com/good-loop/doc/wiki/Canonical-Terminology-for-Logging-Good-Loop-Events
		return getDataLogData({ q: query==="all"?null:query, breakdowns, op, start, end, incs, ince, dataspace, prob, interval, size });
	});
	const mydata = pvMydata?.value;

	// display...
	return (
		<div className="page DashboardPage">
			<h2>My Dashboard</h2>

			<p>One month of data, in per-hour segments. Near realtime: Does NOT include the most recent 15 minutes.</p>
			<p>Endpoint: {ServerIO.DATALOG_ENDPOINT}</p>
			<FiltersWidget />

			{pvMydata && !pvMydata.resolved && <Misc.Loading />}

			{mydata && <DataDisplay data={mydata} byx={breakdown} />}
		</div>
	);
} // ./DashboardPage

function DataDisplay({data, byx}) {
	let data0 = pivot(data, `by_${byx}.buckets.$bi.{key, count}`, '$key.$count');
	let chartDataOptions = timeSeriesChartFromKeyValue(data0);
	console.log('key:count', data0);
	
	let columns = [byx, "count"];

let tableData = Object.keys(data0).map(k => {
	let kobj = data0[k];
	let row = {count:kobj};
	row[byx] = k;
	// HACK time
	if (byx==="time") row[byx] = isoDate(new Date(asNum(k)));
	return row;
});

	// { labels:string[], datasets:[{label, data:number[]}] }
	let numseries = Object.values(data0);
	let chartData = {labels:[byx], datasets:[{label:byx, numseries}]};

	let activeTabId = DataStore.getUrlValue("tab");
 	let setActiveTabId = tabId =>  DataStore.setUrlValue("tab",tabId);

	return (<Tabs activeTabId={activeTabId} setActiveTabId={setActiveTabId} >
				<Tab title="Time-Series Chart">
					<NewChartWidget data={chartDataOptions.data} options={chartDataOptions.options} height={null} width={null} />
				</Tab>
				<Tab title="Table">
					<SimpleTable columns={columns} data={tableData} hasCsv />
				</Tab>
				<Tab title="Bar Chart">
					<NewChartWidget type="bar" data={chartDataOptions.data} options={chartDataOptions.options} height={null} width={null} />
				</Tab>
				<Tab title="Examples">
					<ExamplesTable examples={data.examples} />
				</Tab>
				<Tab title="Examples: Scatter Plots">
					<ScatterPlots examples={data.examples} />
				</Tab>
			</Tabs>);
};

const filtersEditorPath = ['widget', 'Dashboard', 'filters.editor'];

const FiltersWidget = () => {
	let filters = DataStore.getValue(filtersEditorPath);
	if ( ! filters) {
		// read unedited valued off the url
		filters = DataStore.getValue(['location', 'params']);
		// and set in the editor (without an update)
		DataStore.setValue(filtersEditorPath, filters, false);
	}
	// click
	const setFilters = () => {
		"d q breakdown op prob start end incs ince interval size".split(" ").forEach(k => DataStore.setUrlValue(k, filters[k]));
		DataStore.update();
	};
	return (<div className='well'>
		<PropControl path={filtersEditorPath} item={filters} prop='d' label='Dataspace' 
			help="E.g. gl or emissions" />
		<PropControl path={filtersEditorPath} item={filters} prop='q' label='Query' type="search" required 
			help="E.g. evt:pxl or source:/.*S3.*/" />
		<PropControl path={filtersEditorPath} item={filters} prop='breakdown' label />
		<PropControl path={filtersEditorPath} item={filters} prop='prob' type="number" label="Probability [0,1] Use -1 for automatic" />
		<PropControlPeriod path={filtersEditorPath} />
		<div className='flex'>
			<PropControl className="mr-2" path={filtersEditorPath} item={filters} prop='incs' label="Include start" type="checkbox" dflt={true} />
			<PropControl path={filtersEditorPath} item={filters} prop='ince' label="Include end" type="checkbox" dflt={false} />
		</div>
		<PropControl path={filtersEditorPath} item={filters} prop='interval' label type="select" options={["day", "hour","15 minutes","5 minutes"]} />

		<PropControl path={filtersEditorPath} item={filters} prop='op' label type="select" 
			options={"sum cardinality histogram stats avg".split(" ")} />
		
		<PropControl path={filtersEditorPath} prop="size" label="Number of examples" type="number" />
		
		<button className='btn btn-default' onClick={setFilters}>Load Data</button>
	</div>);
};


function ExamplesTable({examples}) {
	if ( ! examples) {
		return "None. Are you logged in properly? "+Login.getId();
	}
	let egs = examples.map(example => Object.assign({id:example._id}, example._source));
	let columns = [
		"id", "evt", "domain", "count"
	];
	let ks = getExampleKeys(egs);
	let kcols = ks.map(k => {
		return {Header:k, accessor:eg => eg[k] || eg.props.find(p => p.k === k)?.n};
	});
	columns = columns.concat(kcols);
	return <SimpleTable data={egs} columns={columns} hasCsv="top" significantDigits={4} precision={4} />
}

function getExampleKeys(examples) {
	// key-value keys and top-level indexed keys
	let ks = uniq(flatten(egs.map(eg => [eg.props.map(prop => prop.k), Object.keys(eg)])));
	ks.sort();
	ks = ks.filter(k => ["id", "props", "domain"].includes(k));
	return ks;
}

function ScatterPlots({examples}) {
	if ( ! examples) {
		return "None. Are you logged in properly? "+Login.getId();
	}
	let egs = examples.map(example => Object.assign({id:example._id}, example._source));
	let ks = getExampleKeys(egs);
	// HACK hardcode some
	// ks = "glAutoBase glAutoSupplyPath glAutoTotal mb mbmbl mbperad mbperadmbl ssps".split(/\s+/);
	return <table border={1}>
		<tr><th></th>{ks.map(k => <th key={k}>{k}</th>)}</tr>
		{ks.map(k => <tr key={k}><th>{k}</th>{ks.map(k2 => <td key={k2}><small>{k} x {k2}</small><ScatterPlot examples={examples} x={k} y={k2} /></td>)}</tr>)}
	</table>;
}

/**
 * https://www.chartjs.org/docs/latest/samples/other-charts/scatter.html
 * @param {*} param0 
 * @returns 
 */
function ScatterPlot({examples, x, y}) {
	let xys = [], labels= []; 
	let accessor = (eg, k) => eg[k] || eg.props.find(p => p.k === k)?.n
	let egs = examples.map(example => Object.assign({id:example._id}, example._source));
	egs.forEach(eg => {
		xys.push([accessor(eg, x), accessor(eg, y)]);
		labels.push(eg.id+" "+eg.domain);
	});
	let data = {
		datasets:[
			{data:xys, labels:labels}
		]
	};
	return <NewChartWidget width={200} height={200} type='scatter' data={data} />
}

export default DashboardPage;
