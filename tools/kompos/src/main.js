requirejs.config({
  paths: {
    'd3': ['https://d3js.org/d3.v3'],
    'ws': ['ws-shim']
  },
  shim: {
    'd3': { exports: 'd3' },
    'ws': { exports: 'ws' }
  }
});

var ctrl;

requirejs(
  ['vm-connection', 'controller', 'source', 'view', 'visualizations'], 
  function(vmConn, cont, src, vw) {
    var view = new vw.View(),
      vmConnection = new vmConn.VmConnection(),
      dbg = new src.Debugger();
    ctrl = new cont.Controller(dbg, view, vmConnection);
    ctrl.toggleConnection();
  });
