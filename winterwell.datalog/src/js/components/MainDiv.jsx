import React, { Component } from 'react';
import _ from 'lodash';
import C from '../C';

// Plumbing
import DataStore from '../base/plumbing/DataStore';
// Templates
import LoginWidget from '../base/components/LoginWidget';
import MessageBar from '../base/components/MessageBar';
import NavBar from '../base/components/NavBar';
// Pages
import DashboardPage from './DashboardPage';
import {BasicAccountPage} from '../base/components/AccountPageWidgets';
import MainDivBase from '../base/components/MainDivBase';
import PropControls from '../base/components/propcontrols/PropControls';
let dummyKeepImport = PropControls;

C.setupDataStore();

const PAGES = {
	dashboard: DashboardPage,
	account: BasicAccountPage,
};

/**
		Top-level: tabs
*/
const MainDiv = () => {
	return <MainDivBase pageForPath={PAGES} defaultPage='dashboard' loginRequired />;
}

export default MainDiv;
