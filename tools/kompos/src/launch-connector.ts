const msgPortRe = /.*Message Handler:\s+(\d+)/m;
const tracePortRe = /.*Trace Handler:\s+(\d+)/m;

/** Parse the data string and retrieve message and trace ports. */
export function determinePorts(data: string, ports: { msg: number, trace: number }) {
  const m1 = data.match(msgPortRe);
  if (m1) {
    if (ports.msg === 0) {
      ports.msg = parseInt(m1[1]);
    }
  }
  const m2 = data.match(tracePortRe);
  if (m2) {
    if (ports.trace === 0) {
      ports.trace = parseInt(m2[1]);
    }
  }
}
