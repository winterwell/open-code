'use strict';
import Enum from 'easy-enums';
import C from './base/CBase';
import Roles from './base/Roles';
export default C;

/**
 * app config
 */
Object.assign(C.app, {
	name: "DataLog",
	service: "datalog",
	logo: "/img/gl-logo/LogoMark/logo.svg",
	dataspace:"good-loop"
});

const TYPES = new Enum("User");
C.ROLES = new Enum("user admin");
C.CAN = new Enum("admin");
// setup roles
Roles.defineRole(C.ROLES.user, []);
Roles.defineRole(C.ROLES.admin, C.CAN.values);

