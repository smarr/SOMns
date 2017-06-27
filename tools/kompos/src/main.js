requirejs.config({
  paths: {
    'd3': ['/node_modules/d3/d3'],
    'ws': ['ws-shim']
  },
  shim: {
    'd3': { exports: 'd3' },
    'ws': { exports: 'ws' }
  }
});

var ctrl;

requirejs(
  ['vm-connection', 'controller', 'ui-controller', 'debugger', 'view',
    'breakpoints'],
  function(vmConn, cont, uiCont, d, vw, bps) {
    $("#graph-canvas").resizable({ handleSelector: '#split-system-code', resizeWidth: false });

    var vmConnection = new vmConn.VmConnection(true);
    ctrl = new uiCont.UiController(vmConnection);
    ctrl.toggleConnection();
  });
