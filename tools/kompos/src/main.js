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
   'visualizations', 'breakpoints'],
  function(vmConn, cont, uiCont, d, vw) {
    $("#graph-canvas").resizable({handleSelector: '#split-system-code', resizeWidth: false});

    var view = new vw.View(),
      vmConnection = new vmConn.VmConnection(true),
      dbg = new d.Debugger();
    ctrl = new uiCont.UiController(dbg, view, vmConnection);
    ctrl.toggleConnection();
  });
