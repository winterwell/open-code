import React from 'react';

import { assert } from '../base/utils/assert';
import { encURI } from '../base/utils/miscutils';
import _ from 'lodash';
import PromiseValue from 'promise-value';

import C from '../C';
import DataStore, { getDataPath, getPath } from '../base/plumbing/DataStore';
import ActionMan from '../plumbing/ActionMan';
import {getType} from '../base/data/DataClass';
import Misc from '../base/components/Misc';
import PropControl from '../base/components/PropControl';
import ListLoad from '../base/components/ListLoad';
import Roles from '../base/Roles';
import KStatus from '../base/data/KStatus';

import { setWindowTitle } from '../base/plumbing/Crud';


const ChatscriptPage = () => {	
	const type = C.TYPES.Chatscript;
	const path = DataStore.getValue(['location','path']);
	const id = path[1];
	setWindowTitle(type);	
	if ( ! id) {
		return <ListLoad type={type} status={KStatus.ALL_BAR_TRASH} 
			// navpage,
			// q, 
			// start, end,
			// sort = 'created-desc',
			// filter, 
			hasFilter
			// , filterLocally,
			// ListItem,
			// checkboxes, 
			canDelete canCreate canFilter
			// createBase,
			// className,
			// noResults,
			// notALink, itemClassName,
			// preferStatus,
			// hideTotal,
			// pageSize,
			// unwrapped
		/>;
	}
	const pvItem = ActionMan.getDataItem({type, id, status:KStatus.DRAFT});
	if ( ! pvItem.value) {
		return (<div><h1>Chat: {id}</h1><Misc.Loading /></div>);
	}
	const item = pvItem.value;
	setWindowTitle(item);	
	return (
		<div className=''>
			<h1>Chat Script: {item.name || id}</h1>
			<EditChatscript item={item} />
		</div>
	);
}; // ./ChatPage


const EditChatscript = ({item}) => {
	assert(item.id, item);
	let path = getDataPath({status:KStatus.DRAFT, type:C.TYPES.Chatscript, id:item.id});
	return (
		<div className="form">
			ID: {item.id}<br />
			<Misc.SavePublishDiscard type={C.TYPES.Chat} id={item.id} />
			<PropControl label="Chat Script Name" item={chat} path={path} prop="name" />
			<PropControl label="Notes" path={path} prop="notes" type="textarea" item={chat} />
		</div>
	);
};

export default ChatscriptPage;
