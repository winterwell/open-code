
import React from 'react';
import ReactDOM from 'react-dom';
import $ from 'jquery';
import Misc from './base/components/Misc';
import MainDiv from './components/MainDiv';
import ServerIO from './plumbing/ServerIO'; // import to set api endpoints

// Import root LESS file so webpack finds & renders it out to main.css
import '../style/main.less';
import { assert } from './base/utils/assert';

assert(true); // keep the import

// Pass font awesome version onto Misc, so it adds the appropiate class to the icons
Misc.FontAwesome = 5;

// global jquery for You-Again
window.$ = $;

ReactDOM.render(
	<MainDiv />,
	document.getElementById('mainDiv')
	);
