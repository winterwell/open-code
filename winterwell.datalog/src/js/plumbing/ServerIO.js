/** 
 * Wrapper for server calls.
 *
 */
import ServerIO from '../base/plumbing/ServerIOBase';


// Default to fetching real data, not test/local
// e.g. green ad tag reporting
ServerIO.DATALOG_ENDPOINT = 'https://lg.good-loop.com/data';

// e.g. dumboard
ServerIO.PORTAL_ENDPOINT = 'https://portal.good-loop.com';

// e.g. task list
ServerIO.APIBASE = 'https://calstat.good-loop.com';

ServerIO.checkBase();


// LARGELY COPY PASTA FROM PORTAL!

/** 
  * @returns Promise {
 * 	by_$breakdown: {String: Money}
 * 	total: {Money},
 * 	stats: {}
 * }
 */
ServerIO.getSpendData = ({q, breakdown, start, end}) => {
	let url = ServerIO.PORTAL_ENDPOINT+'/datafn/sum';
	const params = {
		data: {q, breakdown, start, end}
	};
	return ServerIO.load(url, params);
};


/**
 * The revenue for a publisher
 * @returns Promise {
 * 	by_vert: {String: Money}
 * 	total: {Money},
 * 	stats: {}
 * }
 */
ServerIO.getRevenueData = ({q, start, end}) => {
	let url = ServerIO.PORTAL_ENDPOINT+'/datafn/sum'; // FIXME DW to write the backend for this!
	const params = {
		data: {q, breakdown:"vert", start, end}
	};
	return ServerIO.load(url, params);
};


/**
 * @param vert {?String} Advert ID. If unset, sum all.
 * @returns Promise {
 * 	total: Number,
 * 	money: Money
 * }
 */
ServerIO.getAllSpend = ({vert}) => {
	let url = ServerIO.PORTAL_ENDPOINT+'/datafn/sum';
	const params = {
		data: {vert}
	};
	return ServerIO.load(url, params);
};


export default ServerIO;
