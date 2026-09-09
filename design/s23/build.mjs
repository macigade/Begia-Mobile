// Builds the S23 artboards for the BEGIA Android canvas.
// One copy of the BEGIA tokens (lifted from IBA-CODE/ui/style.css, --fs-scale 1),
// ten screens, one canvas.json. Run: node build.mjs
import { writeFileSync } from "node:fs";

// ---------------------------------------------------------------- tokens ----
const CSS = `
:root {
  --bg: #0e141a; --panel: #151d25; --panel2: #1a242e; --line: #263340;
  --ink: #dbe4ec; --muted: #8fa1b0; --muted3: #6a7a89;
  --accent: #3fb6c0; --accent-dim: #17444a; --rec: #e5534b; --warn: #d9a03c;
  --confirm-bg: #14251c; --confirm-border: #2c5138; --confirm-fg: #6bd48a;
  --fault-bg: #3b1512; --fault-fg: #ff9a92;
  --mono: "IBM Plex Mono", Consolas, "DejaVu Sans Mono", monospace;
  --sans: "IBM Plex Sans", "Segoe UI", system-ui, sans-serif;
  --wordmark: #eaf1f7;
}
* { box-sizing: border-box; }
body { margin: 0; background: #0e141a; color: var(--ink); font-family: var(--sans); font-size: 14px; line-height: 1.45; }
a { color: #3fb6c0; } a:hover { color: #7fe3ea; }
.mono { font-family: var(--mono); font-size: 12px; }
.muted { color: var(--muted); }
.muted3 { color: var(--muted3); }
.tabular { font-variant-numeric: tabular-nums; }

/* the phone: 411 x 891 dp, S23 at 2.625x. The system owns the top 28 and the bottom 20. */
.phone { position: relative; width: 411px; height: 891px; overflow: hidden; background: var(--bg); display: flex; flex-direction: column; }
.phone.land { width: 891px; height: 411px; }
.safe-top { height: 28px; flex: 0 0 auto; }
.safe-bot { height: 20px; flex: 0 0 auto; background: var(--bg); }

/* top strip: the state chip in words, the window, LIVE */
.strip { display: flex; align-items: center; gap: 8px; padding: 6px 8px; height: 56px; flex: 0 0 auto; }
.strip .statechip { min-height: 44px; }
.iconbtn { width: 44px; height: 44px; display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; border-radius: 8px; }
.mark { width: 33px; height: 22px; flex: 0 0 auto; }
.statechip { display: flex; align-items: center; gap: 8px; min-width: 0; padding: 5px 11px; border: 1px solid var(--line); border-radius: 9px; background: var(--panel2); }
.statechip .sc-label { font-weight: 600; white-space: nowrap; }
.statechip .mono { color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.statechip.bad { border-color: var(--warn); } .statechip.bad .mono { color: var(--warn); }
.led { width: 10px; height: 10px; border-radius: 50%; background: #555; flex: 0 0 auto; }
.led.green { background: #35c46a; box-shadow: 0 0 8px #35c46a88; }
.led.yellow { background: var(--warn); box-shadow: 0 0 8px #d9a03c88; }
.led.red { background: var(--rec); box-shadow: 0 0 8px #e5534b88; }
.spacer { flex: 1; }

.btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; background: var(--panel2); border: 1px solid var(--line); color: var(--ink); border-radius: 8px; padding: 6px 14px; font: inherit; font-size: 14px; font-weight: 600; white-space: nowrap; }
.btn.accent { background: var(--accent-dim); border-color: var(--accent); color: var(--accent); }
.btn.rec { border-color: var(--rec); color: var(--rec); }
.btn.rec.active { background: var(--rec); color: #fff; border-color: var(--rec); }
/* the toolbar variant, at the phone's 44 px floor for anything a thumb hits */
.btn.small { min-height: 44px; padding: 0 12px; font-size: 12px; font-weight: 400; border-radius: 6px; }
.btn.tall { min-height: 44px; padding: 6px 18px; }
.btn.block { width: 100%; }

.chip { font-family: var(--mono); font-size: 10px; font-weight: 600; letter-spacing: .1em; padding: 2px 8px; border-radius: 10px; border: 1px solid var(--line); color: var(--muted); white-space: nowrap; }
.chip.armed { border-color: var(--warn); color: var(--warn); }
.chip.recording { border-color: var(--rec); color: var(--rec); }
.chip.ok { border-color: var(--confirm-border); color: var(--confirm-fg); }

.wsel { display: flex; gap: 2px; background: var(--panel2); border: 1px solid var(--line); border-radius: 8px; padding: 2px; min-height: 44px; align-items: stretch; }
.wsel-opt { display: inline-flex; align-items: center; background: none; border: none; color: var(--muted); font-family: var(--sans); font-size: 12.5px; padding: 4px 10px; border-radius: 6px; }
.wsel-opt.active { background: var(--accent-dim); color: var(--accent); font-weight: 600; }

.field { display: flex; align-items: center; gap: 8px; width: 100%; min-height: 44px; background: var(--panel2); border: 1px solid var(--line); border-radius: 7px; color: var(--ink); padding: 9px 10px; font-size: 13px; }
.field.focus { outline: 1px solid var(--accent); }
.field .ph { color: var(--muted); }

.card { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 14px 15px; }
.card h2 { margin: 0 0 10px; font-size: 12px; font-weight: 600; color: var(--muted); display: flex; align-items: center; gap: 8px; }
.card h2 .count { color: var(--accent); }
.row { display: flex; gap: 8px; align-items: center; }

/* content column */
.page { flex: 1 1 auto; min-height: 0; overflow: hidden; display: flex; flex-direction: column; gap: 8px; padding: 0 8px; }

/* a pane: the same card the desktop draws, at phone width */
.pane { background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 6px 10px 4px; flex: 0 0 auto; }
.pane-head { display: flex; align-items: baseline; gap: 12px; font-family: var(--mono); font-size: 12px; padding: 2px 0 4px; }
.ph-sig { display: flex; flex-direction: column; gap: 1px; min-width: 0; flex: 1 1 auto; }
.ph-row1 { display: flex; align-items: baseline; gap: 7px; min-width: 0; }
.ph-row2 { display: flex; align-items: center; gap: 6px; padding-left: 15px; }
.dot { width: 8px; height: 8px; border-radius: 2px; align-self: center; flex: 0 0 auto; }
.nm { color: var(--ink); font-weight: 600; font-size: 15px; font-family: var(--sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.val { color: var(--ink); font-weight: 600; font-size: 22px; text-align: right; font-variant-numeric: tabular-nums; flex: 0 0 auto; margin-left: auto; }
.u { color: var(--muted); font-size: 12.5px; }
.ph-addr { color: var(--muted3); font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pane-live { font-family: var(--mono); font-size: 10px; font-weight: 600; letter-spacing: .08em; padding: 1px 6px; border: 1px solid var(--line); border-radius: 4px; color: var(--muted); align-self: center; }
.pane-live.on { color: var(--accent); background: var(--accent-dim); border-color: var(--accent); }
.taxis { display: flex; justify-content: space-between; font-family: var(--mono); font-size: 10px; color: var(--muted); padding: 2px 2px 0; }
.chart { display: block; }

/* digital lane: 22 px, filled where TRUE */
.lane { display: flex; align-items: center; gap: 6px; background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 8px 10px; flex: 0 0 auto; }
.lane .nm { font-size: 13px; width: 112px; flex: 0 0 auto; }
.lane .lv { font-family: var(--mono); font-size: 12px; font-weight: 600; width: 44px; text-align: right; flex: 0 0 auto; }

/* trial bar, docked above the tabs */
.trialbar { display: flex; flex-direction: column; gap: 6px; flex: 0 0 auto; padding: 8px 10px; background: var(--panel); border-top: 1px solid var(--line); }
.tb-row { display: flex; align-items: center; gap: 8px; }
.tb-rec { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; height: 44px; padding: 5px 12px; border-radius: 9px; background: var(--rec); color: #fff; font-family: var(--mono); font-weight: 700; font-size: 14px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.rec-dot { width: 10px; height: 10px; border-radius: 50%; background: #fff; }
.tb-file { color: var(--muted3); font-family: var(--mono); font-size: 11.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qm { display: inline-flex; align-items: center; gap: 6px; height: 44px; padding: 0 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--panel2); font-size: 12.5px; white-space: nowrap; }
.qm b { font-family: var(--mono); font-size: 11px; color: var(--accent); }
.tsent { font-family: var(--mono); font-size: 11.5px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.banner { padding: 6px 12px; background: #3a2a10; color: var(--warn); border-bottom: 1px solid #57431c; font-family: var(--mono); font-size: 12px; }
.banner.fault { background: var(--fault-bg); color: var(--fault-fg); border-bottom-color: #6b2a22; }
.banner.confirm { background: var(--confirm-bg); color: var(--confirm-fg); border-bottom-color: var(--confirm-border); }

/* tab bar */
.tabs { display: flex; flex: 0 0 auto; height: 56px; background: var(--panel); border-top: 1px solid var(--line); }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; color: var(--muted); font-size: 11px; font-weight: 600; border-top: 3px solid transparent; }
.tab.active { color: var(--accent); border-top-color: var(--accent); }
.tab svg { width: 22px; height: 22px; }

/* lists */
.lrow { display: flex; align-items: center; gap: 10px; min-height: 52px; padding: 7px 8px; border-bottom: 1px solid var(--line); }
.lrow:last-child { border-bottom: none; }
.lrow .t1 { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lrow .t2 { font-family: var(--mono); font-size: 11px; color: var(--muted3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lrow .grow { flex: 1; min-width: 0; }
.sect { font-family: var(--mono); font-size: 10.5px; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); padding: 10px 8px 4px; }
.cb { width: 22px; height: 22px; border: 1px solid var(--muted3); border-radius: 5px; flex: 0 0 auto; display: flex; align-items: center; justify-content: center; }
.cb.on { background: var(--accent); border-color: var(--accent); color: #06222a; }
.cb svg { width: 14px; height: 14px; }
.tchip { font-family: var(--mono); font-size: 10px; color: var(--muted); border: 1px solid var(--line); border-radius: 4px; padding: 0 5px; flex: 0 0 auto; }
.hit { color: var(--accent); }
.chev { color: var(--muted3); flex: 0 0 auto; }
.ico { width: 20px; height: 20px; flex: 0 0 auto; color: var(--muted); }

/* bottom sheet */
.sheet { position: absolute; left: 0; right: 0; bottom: 0; background: var(--panel); border-top: 1px solid var(--line); border-radius: 14px 14px 0 0; padding: 8px 8px 20px; box-shadow: 0 -12px 32px rgba(0,0,0,.45); }
.sheet .grip { width: 36px; height: 4px; border-radius: 2px; background: var(--line); margin: 2px auto 10px; }
.scrim { position: absolute; inset: 0; background: rgba(0,0,0,.55); }
.act { display: flex; align-items: center; gap: 12px; min-height: 48px; padding: 6px 10px; font-size: 14.5px; }
.act .ico { color: var(--ink); }
.act.danger { color: var(--rec); } .act.danger .ico { color: var(--rec); }

/* boot */
.word { font-family: "Chakra Petch", "Bahnschrift", "Segoe UI", system-ui, sans-serif; font-weight: 700; letter-spacing: .02em; line-height: 1; font-size: 38px; }
.sw-beg { color: #169dd8; } .sw-ia { color: #eaf1f7; }
.bar { height: 3px; background: var(--panel2); border-radius: 2px; overflow: hidden; }
.bar i { display: block; height: 100%; background: var(--accent); }

/* sheet of states */
.sheetlbl { font-family: var(--mono); font-size: 10.5px; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); padding: 0 2px 6px; }
.frame { border: 1px solid var(--line); border-radius: 8px; overflow: hidden; background: var(--bg); }
`;

// ----------------------------------------------------------------- icons ----
const svg = (body, cls = "ico") => `<svg class="${cls}" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
const I = {
  live: svg('<polyline points="2 11 5.5 11 7.5 5 10.5 15 12.5 9 14 11 18 11"></polyline>'),
  trials: svg('<rect x="3" y="4" width="14" height="13" rx="2"></rect><circle cx="10" cy="10.5" r="2.6" fill="currentColor" stroke="none"></circle>'),
  signals: svg('<circle cx="5" cy="5" r="2"></circle><circle cx="5" cy="15" r="2"></circle><circle cx="15" cy="10" r="2"></circle><path d="M7 5.5 L13 9.3 M7 14.5 L13 10.7"></path>'),
  setup: svg('<path d="M3 6h14 M3 10h14 M3 14h14"></path><circle cx="7" cy="6" r="1.8" fill="#151d25"></circle><circle cx="13" cy="10" r="1.8" fill="#151d25"></circle><circle cx="8" cy="14" r="1.8" fill="#151d25"></circle>'),
  search: svg('<circle cx="8.5" cy="8.5" r="5"></circle><path d="M12.5 12.5 L17 17"></path>'),
  check: svg('<polyline points="4 10.5 8 14.5 16 6"></polyline>', ""),
  chev: svg('<polyline points="7 4 13 10 7 16"></polyline>', "ico chev"),
  back: svg('<polyline points="12 4 6 10 12 16"></polyline>'),
  share: svg('<path d="M10 12V3 M6.5 6.5 10 3l3.5 3.5"></path><path d="M4 10v6a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1v-6"></path>'),
  laptop: svg('<rect x="4" y="4" width="12" height="9" rx="1.5"></rect><path d="M2 16h16"></path>'),
  file: svg('<path d="M5 3h7l4 4v10H5z"></path><path d="M12 3v4h4"></path>'),
  trash: svg('<path d="M4 6h12 M8 6V4h4v2 M6 6l1 11h6l1-11"></path>'),
  open: svg('<rect x="3" y="4" width="14" height="12" rx="2"></rect><polyline points="6 12 9 8 11 11 14 8"></polyline>'),
  plc: svg('<rect x="3" y="5" width="14" height="10" rx="1.5"></rect><path d="M6 8h3 M6 11h6 M14 8h1"></path>'),
  set: svg('<rect x="3" y="3" width="6" height="6" rx="1"></rect><rect x="11" y="3" width="6" height="6" rx="1"></rect><rect x="3" y="11" width="6" height="6" rx="1"></rect><rect x="11" y="11" width="6" height="6" rx="1"></rect>'),
  app: svg('<circle cx="10" cy="10" r="7"></circle><path d="M10 6v4l3 2"></path>'),
  opt: svg('<circle cx="10" cy="10" r="3"></circle><path d="M10 2v2 M10 16v2 M2 10h2 M16 10h2 M4.3 4.3l1.5 1.5 M14.2 14.2l1.5 1.5 M4.3 15.7l1.5-1.5 M14.2 5.8l1.5-1.5"></path>'),
  diag: svg('<path d="M3 15 L7 8 L10 12 L13 5 L17 15"></path>'),
  wifi: svg('<path d="M2.5 7.5a11 11 0 0 1 15 0 M5.5 10.5a7 7 0 0 1 9 0 M8.5 13.5a3 3 0 0 1 3 0"></path><circle cx="10" cy="16" r="1" fill="currentColor"></circle>'),
  warn: svg('<path d="M10 3 L18 17 H2 Z"></path><path d="M10 8v4 M10 14.5v.5"></path>'),
  mark: svg('<path d="M5 17V3h9l-2 4 2 4H5"></path>'),
  play: svg('<circle cx="10" cy="10" r="4.5" fill="currentColor" stroke="none"></circle>', "ico"),
  vol: svg('<rect x="3" y="7" width="4" height="6" rx="1"></rect><path d="M7 7l5-3v12l-5-3"></path><path d="M14.5 8a3 3 0 0 1 0 4"></path>'),
  clear: svg('<circle cx="10" cy="10" r="7"></circle><path d="M7.5 7.5l5 5 M12.5 7.5l-5 5"></path>'),
};
const esc = (s) => s.replace(/&/g, "&amp;").replace(/"/g, "&quot;");

// ---------------------------------------------------------------- charts ----
function trace(fn, w, h, n = 140) {
  let d = "";
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    const y = Math.min(1, Math.max(0, fn(t)));
    d += (i ? " L" : "M") + (t * w).toFixed(1) + " " + (h - y * h).toFixed(1);
  }
  return d;
}
const noise = (t, k, a) => Math.sin(t * k * 7.1) * a + Math.sin(t * k * 13.7 + 1) * a * 0.6;
const FN = {
  speed: (t) => (t < 0.18 ? 0.08 + t * 3.6 : 0.73 + noise(t, 3, 0.012) + (t > 0.8 ? -(t - 0.8) * 0.9 : 0)),
  torque: (t) => 0.5 + Math.sin(t * 9) * 0.22 + noise(t, 5, 0.03),
  current: (t) => 0.42 + Math.sin(t * 9 + 0.4) * 0.16 + noise(t, 4, 0.02),
  pressure: (t) => { const c = (t * 3) % 1; return 0.25 + c * 0.55 - (c > 0.85 ? (c - 0.85) * 3 : 0) + noise(t, 6, 0.008); },
};
// A chart stand-in: grid, traces, a value tag on the axis each trace is measured
// against, drawn in the trace colour. Never port this SVG - it stands for uPlot.
function chart({ w = 371, h = 80, traces, marker = null }) {
  // one axis column per trace, each wide enough for its own value tag
  const tagW = (t) => Math.round(t.length * 5.9 + 6);
  const colW = Math.max(...traces.map((t) => tagW(t.tag))) + 3;
  const pw = w - (traces.length * colW + 4);
  let s = `<svg class="chart" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" aria-hidden="true">`;
  s += `<rect x="0" y="0" width="${w}" height="${h}" fill="#151d25"></rect>`;
  for (const f of [0.25, 0.5, 0.75]) s += `<line x1="0" x2="${pw}" y1="${(h * f).toFixed(1)}" y2="${(h * f).toFixed(1)}" stroke="rgba(255,255,255,.07)"></line>`;
  for (let i = 1; i < 6; i++) s += `<line y1="0" y2="${h}" x1="${(pw * i / 6).toFixed(1)}" x2="${(pw * i / 6).toFixed(1)}" stroke="rgba(255,255,255,.07)"></line>`;
  traces.forEach((tr, i) => {
    const ax = pw + 3 + i * colW;
    s += `<line x1="${ax}" x2="${ax}" y1="0" y2="${h}" stroke="${tr.color}" stroke-opacity=".55" stroke-width="1.5"></line>`;
    s += `<path d="${trace(tr.fn, pw, h)}" stroke="${tr.color}" stroke-width="1.6" fill="none"></path>`;
    const yEnd = h - Math.min(1, Math.max(0, tr.fn(1))) * h;
    const ty = Math.min(h - 8, Math.max(8, yEnd));
    s += `<rect x="${ax - 1}" y="${(ty - 7).toFixed(1)}" width="${tagW(tr.tag)}" height="14" rx="2" fill="${tr.color}"></rect>`;
    s += `<text x="${ax + 2}" y="${(ty + 3.5).toFixed(1)}" font-family="IBM Plex Mono, Consolas, monospace" font-size="9.5" font-weight="600" fill="#06222a">${tr.tag}</text>`;
  });
  if (marker) {
    const mx = (pw * marker.at).toFixed(1);
    s += `<line x1="${mx}" x2="${mx}" y1="0" y2="${h}" stroke="#d9a03c" stroke-dasharray="3 3" stroke-width="1.2"></line>`;
    s += `<rect x="${(+mx + 3).toFixed(1)}" y="2" width="22" height="13" rx="2" fill="#d9a03c"></rect><text x="${(+mx + 6).toFixed(1)}" y="12" font-family="IBM Plex Mono, Consolas, monospace" font-size="9.5" font-weight="600" fill="#1a1204">${marker.label}</text>`;
  }
  s += `</svg>`;
  return s;
}
function laneSvg({ w = 193, h = 22, color, segs, marker = null }) {
  let s = `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" aria-hidden="true"><rect x="0" y="0" width="${w}" height="${h}" rx="3" fill="#1a242e"></rect>`;
  for (const [a, b] of segs) {
    const x = (a * w).toFixed(1), ww = ((b - a) * w).toFixed(1);
    s += `<rect x="${x}" y="2" width="${ww}" height="${h - 4}" fill="${color}" fill-opacity=".28"></rect><rect x="${x}" y="2" width="${ww}" height="2" fill="${color}"></rect>`;
  }
  if (marker) s += `<line x1="${(marker * w).toFixed(1)}" x2="${(marker * w).toFixed(1)}" y1="0" y2="${h}" stroke="#d9a03c" stroke-dasharray="3 3"></line>`;
  return s + `</svg>`;
}

// -------------------------------------------------------------- fragments ----
const C = { speed: "#4fc1e9", torque: "#ed5565", current: "#ac92ec", running: "#a0d468", pressure: "#5d9cec", valve: "#f5a623" };

const strip = ({ live = true, win = "1 min", state = "ok" } = {}) => `
<div class="strip">
  <img class="mark" src="begia-mark.svg" alt="BEGIA">
  <div class="statechip${state === "bad" ? " bad" : ""}" style="flex: 1 1 auto; min-width: 0;">
    <span class="led ${state === "bad" ? "yellow" : "green"}"></span>
    <span class="sc-label">Connected</span>
    <span class="mono">EAF 1 · 100 ms</span>
  </div>
  <div class="wsel"><span class="wsel-opt active">${win}</span></div>
  <span class="btn small${live ? " accent" : ""}">LIVE</span>
</div>`;

const tabs = (active) => `
<div class="tabs">
  <div class="tab${active === "live" ? " active" : ""}">${I.live}<span>Live</span></div>
  <div class="tab${active === "trials" ? " active" : ""}">${I.trials}<span>Trials</span></div>
  <div class="tab${active === "signals" ? " active" : ""}">${I.signals}<span>Signals</span></div>
  <div class="tab${active === "setup" ? " active" : ""}">${I.setup}<span>Setup</span></div>
</div>
<div class="safe-bot"></div>`;

const paneHead = (sigs, opts = {}) => `
<div class="pane-head">
  ${sigs.map((s) => `
  <div class="ph-sig" style="flex: ${s.grow ?? "1 1 auto"};">
    <div class="ph-row1"><span class="dot" style="background: ${s.color};"></span><span class="nm">${s.name}</span><span class="val" style="color: ${s.color};">${s.val}</span><span class="u">${s.unit}</span></div>
    <div class="ph-row2"><span class="ph-addr">${s.addr}</span></div>
  </div>`).join("")}
  ${opts.live === false ? "" : `<span class="pane-live on">LIVE</span>`}
</div>`;

const taxis = (labels = ["-60 s", "-45 s", "-30 s", "-15 s", "now"]) => `<div class="taxis">${labels.map((l) => `<span>${l}</span>`).join("")}</div>`;

const paneSpeed = (m) => `
<div class="pane">
  ${paneHead([{ color: C.speed, name: "Drive speed", val: "1214", unit: "rpm", addr: '"DB_Drive"."Speed"' }])}
  ${chart({ traces: [{ color: C.speed, fn: FN.speed, tag: "1214" }], marker: m })}
  ${taxis()}
</div>`;
const paneTorque = (m) => `
<div class="pane">
  ${paneHead([
    { color: C.torque, name: "Torque", val: "62.4", unit: "%", addr: '"DB_Drive"."Torque"' },
    { color: C.current, name: "Current", val: "418", unit: "A", addr: '"DB_Drive"."Current"' },
  ])}
  ${chart({ traces: [{ color: C.torque, fn: FN.torque, tag: "62.4" }, { color: C.current, fn: FN.current, tag: "418", tagW: 26 }], marker: m })}
  ${taxis()}
</div>`;
const panePressure = (m) => `
<div class="pane">
  ${paneHead([{ color: C.pressure, name: "Hydraulics", val: "186.2", unit: "bar", addr: '"DB_Hydraulic"."Pressure"' }])}
  ${chart({ traces: [{ color: C.pressure, fn: FN.pressure, tag: "186.2", tagW: 32 }], marker: m })}
  ${taxis()}
</div>`;
const lane = (name, addr, color, segs, value, m) => `
<div class="lane">
  <span class="dot" style="background: ${color};"></span>
  <span class="nm" title="${esc(addr)}">${name}</span>
  ${laneSvg({ color, segs, marker: m })}
  <span class="lv" style="color: ${value === "TRUE" ? color : "var(--muted)"};">${value}</span>
</div>`;

const trialIdle = () => `
<div class="trialbar">
  <div class="tb-row">
    <div class="field" style="flex: 1 1 auto;"><span class="mono muted3">name</span><span>EAF1_tapping_04</span></div>
    <span class="btn rec tall">${I.play}<span>Start</span></span>
  </div>
</div>`;
const trialArmed = () => `
<div class="trialbar">
  <div class="tb-row">
    <span class="chip armed" style="height: 28px; display: inline-flex; align-items: center;">ARMED</span>
    <span class="tsent" style="flex: 1 1 auto;">when Drive speed rises above 1 000 rpm · 5 s before · 20 s</span>
    <span class="btn tall">Disarm</span>
  </div>
</div>`;
const trialRec = ({ fault = false } = {}) => `
${fault ? `<div class="banner fault">NOT SAVING — last write failed at 11:43:07, the file has stopped growing</div>` : ""}
<div class="trialbar">
  <div class="tb-row" style="gap: 6px;">
    <span class="qm"><b>1</b>Tap open</span><span class="qm"><b>2</b>Arc on</span><span class="qm"><b>3</b>Slag door</span>
    <span class="spacer"></span>
    <span class="tb-file">${fault ? "1.8 MB, not growing" : "1.8 MB, growing"}</span>
  </div>
  <div class="tb-row">
    <span class="tb-rec"><span class="rec-dot"></span><span>REC 00:41</span></span>
    <span class="btn accent tall" style="flex: 1 1 auto; font-size: 16px;">${I.mark}<span>Mark</span></span>
    <span class="btn rec active tall">Stop</span>
  </div>
</div>`;

// --------------------------------------------------------------- screens ----
const HEAD = `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;600&amp;family=IBM+Plex+Mono:wght@400;500;600;700&amp;family=Chakra+Petch:wght@700&amp;display=swap">
  <style>${CSS}</style>
</helmet>
`;
const TAIL = `
</x-dc>
</body>
</html>
`;
const wrap = (body) => HEAD + body + TAIL;

const screens = {};

// 1. Live, portrait, nothing recording ------------------------------------
screens["Main"] = wrap(`
<div class="phone">
  <div class="safe-top"></div>
  ${strip()}
  <div class="page">
    ${paneSpeed()}
    ${paneTorque()}
    ${lane("Running", '"DB_Drive"."Running"', C.running, [[0.16, 1]], "TRUE")}
    ${panePressure()}
    ${lane("Valve open", '"DB_Hydraulic"."ValveOpen"', C.valve, [[0.2, 0.46], [0.62, 0.88]], "FALSE")}
  </div>
  ${trialIdle()}
  ${tabs("live")}
</div>`);

// 2. Live, portrait, recording ---------------------------------------------
screens["LiveRecording"] = wrap(`
<div class="phone">
  <div class="safe-top"></div>
  ${strip()}
  <div class="page">
    ${paneSpeed({ at: 0.62, label: "M1" })}
    ${paneTorque({ at: 0.62, label: "M1" })}
    ${lane("Running", '"DB_Drive"."Running"', C.running, [[0.16, 1]], "TRUE", 0.62)}
    ${panePressure({ at: 0.62, label: "M1" })}
    ${lane("Valve open", '"DB_Hydraulic"."ValveOpen"', C.valve, [[0.2, 0.46], [0.62, 0.88]], "FALSE", 0.62)}
  </div>
  ${trialRec()}
  ${tabs("live")}
</div>`);

// 3. Live, landscape, one pane full screen ---------------------------------
screens["LiveLandscape"] = wrap(`
<div class="phone land" style="flex-direction: row;">
  <div style="width: 24px; flex: 0 0 auto;"></div>
  <div style="flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column;">
    <div style="height: 24px; flex: 0 0 auto;"></div>
    <div class="pane-head" style="padding: 4px 6px 6px; gap: 18px; align-items: center;">
      <div class="ph-sig" style="flex: 0 1 auto;">
        <div class="ph-row1"><span class="dot" style="background: ${C.torque};"></span><span class="nm">Torque</span><span class="val" style="color: ${C.torque}; margin-left: 10px;">62.4</span><span class="u">%</span></div>
        <div class="ph-row2"><span class="ph-addr">"DB_Drive"."Torque"</span></div>
      </div>
      <div class="ph-sig" style="flex: 0 1 auto;">
        <div class="ph-row1"><span class="dot" style="background: ${C.current};"></span><span class="nm">Current</span><span class="val" style="color: ${C.current}; margin-left: 10px;">418</span><span class="u">A</span></div>
        <div class="ph-row2"><span class="ph-addr">"DB_Drive"."Current"</span></div>
      </div>
      <span class="spacer"></span>
      <span class="mono muted">Torque / current · pane 2 of 5</span>
      <div class="wsel"><span class="wsel-opt active">1 min</span></div>
      <span class="pane-live on">LIVE</span>
    </div>
    <div style="flex: 1 1 auto; min-height: 0; padding: 0 6px;">
      ${chart({ w: 745, h: 278, traces: [{ color: C.torque, fn: FN.torque, tag: "62.4" }, { color: C.current, fn: FN.current, tag: "418", tagW: 26 }], marker: { at: 0.62, label: "M1" } })}
      ${taxis(["-60 s", "-50 s", "-40 s", "-30 s", "-20 s", "-10 s", "now"])}
    </div>
    <div style="display: flex; justify-content: center; gap: 6px; padding: 4px 0 8px;">
      <span style="width: 6px; height: 6px; border-radius: 50%; background: var(--line);"></span>
      <span style="width: 6px; height: 6px; border-radius: 50%; background: var(--accent);"></span>
      <span style="width: 6px; height: 6px; border-radius: 50%; background: var(--line);"></span>
      <span style="width: 6px; height: 6px; border-radius: 50%; background: var(--line);"></span>
      <span style="width: 6px; height: 6px; border-radius: 50%; background: var(--line);"></span>
    </div>
  </div>
  <div style="width: 104px; flex: 0 0 auto; display: flex; flex-direction: column; align-items: stretch; gap: 8px; padding: 32px 10px 12px 4px; border-left: 1px solid var(--line); background: var(--panel);">
    <span class="tb-rec" style="justify-content: center; padding: 5px 6px;"><span class="rec-dot"></span><span>00:41</span></span>
    <span class="btn accent" style="height: 96px; flex-direction: column; font-size: 16px;">${I.mark}<span>Mark</span></span>
    <span class="qm" style="justify-content: center;"><b>1</b>Tap</span>
    <span class="qm" style="justify-content: center;"><b>2</b>Arc</span>
    <span class="spacer"></span>
    <span class="btn rec active" style="min-height: 44px;">Stop</span>
  </div>
</div>`);

// 4. Trial bar, the four states ---------------------------------------------
screens["TrialBar"] = wrap(`
<div style="width: 411px; background: var(--bg); padding: 14px 0 10px; display: flex; flex-direction: column; gap: 18px;">
  <div style="padding: 0 8px;"><div class="sheetlbl">Idle — nothing recording</div><div class="frame">${trialIdle()}</div></div>
  <div style="padding: 0 8px;"><div class="sheetlbl">Armed — waits for the trigger, hands off</div><div class="frame">${trialArmed()}</div></div>
  <div style="padding: 0 8px;"><div class="sheetlbl">Recording — Mark is the biggest thing on screen</div><div class="frame">${trialRec()}</div></div>
  <div style="padding: 0 8px;"><div class="sheetlbl">Recording, file not growing — red, undismissable</div><div class="frame">${trialRec({ fault: true })}</div></div>
  <div style="padding: 0 8px;">
    <div class="sheetlbl">Hardware</div>
    <div class="card" style="display: flex; gap: 12px; align-items: center;">
      ${I.vol}
      <div style="font-size: 13px; line-height: 1.4;">While recording, <b>volume-down stamps a mark</b> without looking at the screen. Volume-up is left alone so the phone still rings.</div>
    </div>
  </div>
</div>`);

// 5. Signals: search first ---------------------------------------------------
const sigRow = (on, block, pre, hit, post, type) => `
<div class="lrow">
  <span class="cb${on ? " on" : ""}">${on ? I.check : ""}</span>
  <div class="grow">
    <div class="t1">${pre}<span class="hit">${hit}</span>${post}</div>
    <div class="t2">"${block}"."${pre}${hit}${post}"</div>
  </div>
  <span class="tchip">${type}</span>
</div>`;
screens["Signals"] = wrap(`
<div class="phone">
  <div class="safe-top"></div>
  ${strip({ live: false })}
  <div class="page" style="gap: 6px;">
    <div class="field focus" style="padding: 0 0 0 10px;">${I.search}<span>torq</span><span class="spacer"></span><span class="iconbtn">${I.clear}</span></div>
    <div class="row" style="padding: 0 2px;">
      <span class="mono muted">41 matches · DB_Drive 12 · DB_Mill 29</span>
      <span class="spacer"></span>
      <span class="btn small" style="padding-right: 6px;">Browse the PLC ${I.chev}</span>
    </div>
    <div class="card" style="padding: 0 4px; flex: 1 1 auto; min-height: 0; overflow: hidden;">
      <div class="sect">DB_Drive</div>
      ${sigRow(true, "DB_Drive", "", "Torq", "ue", "REAL")}
      ${sigRow(true, "DB_Drive", "", "Torq", "ueSetpoint", "REAL")}
      ${sigRow(false, "DB_Drive", "", "Torq", "ueLimitHi", "REAL")}
      ${sigRow(false, "DB_Drive", "", "Torq", "ueLimitActive", "BOOL")}
      <div class="sect">DB_Mill</div>
      ${sigRow(true, "DB_Mill", "Roll", "Torq", "ue", "REAL")}
      ${sigRow(false, "DB_Mill", "Roll", "Torq", "ueRef", "REAL")}
      ${sigRow(false, "DB_Mill", "Spindle", "Torq", "ue", "REAL")}
      ${sigRow(false, "DB_Mill", "Spindle", "Torq", "ueRef", "REAL")}
      ${sigRow(false, "DB_Mill", "Spindle", "Torq", "ueOK", "BOOL")}
      ${sigRow(false, "DB_Mill", "Roll", "Torq", "ueLimit", "REAL")}
    </div>
  </div>
  <div class="trialbar" style="gap: 8px;">
    <div class="tb-row">
      <span style="font-weight: 600;">3 selected</span>
      <div class="wsel"><span class="wsel-opt active">one pane each</span><span class="wsel-opt">one pane</span></div>
      <span class="spacer"></span>
      <span class="btn accent tall">Add 3</span>
    </div>
  </div>
  ${tabs("signals")}
</div>`);

// 6. Trials list with a trial's actions ---------------------------------------
const trow = (name, info, rec = false) => `
<div class="lrow">
  ${rec ? `<span class="tb-rec" style="height: 32px; padding: 3px 9px; font-size: 12px;"><span class="rec-dot"></span><span>REC</span></span>` : `<span class="dot" style="width: 8px; height: 8px; background: var(--muted3);"></span>`}
  <div class="grow"><div class="t1">${name}</div><div class="t2">${info}</div></div>
  ${I.chev}
</div>`;
screens["Trials"] = wrap(`
<div class="phone">
  <div class="safe-top"></div>
  ${strip({ live: false })}
  <div class="page">
    <div class="card" style="padding: 4px 4px 0;">
      <h2 style="padding: 8px 8px 0;">Trials <span class="count">7</span><span class="spacer"></span><span class="mono muted3">trials/ · 12.6 MB</span></h2>
      ${trow("EAF1_tapping_04", "now · 00:41 · 6 signals · growing", true)}
      ${trow("EAF1_tapping_03", "today 11:42 · 40 s · 6 signals · 1.8 MB")}
      ${trow("EAF1_tapping_02", "today 11:07 · 38 s · 6 signals · 1.7 MB")}
      ${trow("slag_door_open_02", "today 09:55 · 2 min 10 s · 4 signals · 2.9 MB")}
      ${trow("slag_door_open_01", "today 09:31 · 1 min 58 s · 4 signals · 2.6 MB")}
      ${trow("auto_7_0912", "yesterday 17:20 · 20 s · 6 signals · 0.9 MB")}
      ${trow("auto_6_0912", "yesterday 17:04 · 20 s · 6 signals · 0.9 MB")}
    </div>
  </div>
  ${tabs("trials")}
  <div class="scrim"></div>
  <div class="sheet">
    <div class="grip"></div>
    <div style="padding: 0 10px 8px;">
      <div style="font-size: 15px; font-weight: 600;">EAF1_tapping_03</div>
      <div class="mono muted3" style="font-size: 11px;">today 11:42 · 40 s · 6 signals · 1.8 MB · BEGIA 0.9 (v0.9-4-g1432fd2)</div>
    </div>
    <div class="act">${I.open}<span>Open</span></div>
    <div class="act">${I.share}<span>Share CSV</span><span class="spacer"></span><span class="mono muted3">100 ms grid</span></div>
    <div class="act">${I.file}<span>Share report</span><span class="spacer"></span><span class="mono muted3">PDF</span></div>
    <div class="act">${I.laptop}<span>Send to laptop</span><span class="spacer"></span><span class="mono muted3">FAT-LAPTOP · on this WiFi</span></div>
    <div class="act danger">${I.trash}<span>Delete</span></div>
  </div>
</div>`);

// 7. Setup ----------------------------------------------------------------------
const srow = (icon, t1, t2, right = I.chev) => `
<div class="lrow">${icon}<div class="grow"><div class="t1">${t1}</div><div class="t2">${t2}</div></div>${right}</div>`;
screens["Setup"] = wrap(`
<div class="phone">
  <div class="safe-top"></div>
  ${strip({ live: false })}
  <div class="page">
    <div class="card" style="padding: 4px 4px 0;">
      <h2 style="padding: 8px 8px 0;">This furnace</h2>
      ${srow(`<span class="led green" style="margin: 0 5px;"></span>`, "EAF 1 · CPU 1517F-3 PN", "s7plus://192.168.0.10 · connected 42 min · 1 lost", `<span class="btn small">Change</span>`)}
      ${srow(I.set, "Furnace basics", "6 signals · 5 panes · tied to EAF 1", `<span class="btn small">Change</span>`)}
      ${srow(I.mark, "Trigger", "when Drive speed rises above 1 000 rpm · re-arm", `<span class="chip">OFF</span>`)}
    </div>
    <div class="card" style="padding: 4px 4px 0;">
      <h2 style="padding: 8px 8px 0;">This app</h2>
      ${srow(I.app, "BEGIA 0.9", "v0.9-4-g1432fd2 · shell 3 · installed 09 Sep", "")}
      <div class="lrow" style="background: var(--confirm-bg); border-radius: 8px; margin: 0 0 4px;">
        ${I.wifi}
        <div class="grow"><div class="t1" style="color: var(--confirm-fg);">Update to 0.10 from the laptop</div><div class="t2">FAT-LAPTOP has v0.10-3-g1a2b3c · 1.6 MB</div></div>
        ${I.chev}
      </div>
    </div>
    <div class="card" style="padding: 4px 4px 0;">
      ${srow(I.opt, "Options", "Carbon · IBM Plex Sans · text size 100 %")}
      ${srow(I.diag, "Diagnostics", "10 ms asked, 10.4 ms achieved · battery unrestricted · 12.4 GB free")}
      ${srow(I.laptop, "Show the laptop's BEGIA instead", "this phone as a second screen for FAT-LAPTOP")}
    </div>
  </div>
  ${tabs("setup")}
</div>`);

// 8. Update -----------------------------------------------------------------------
screens["Update"] = wrap(`
<div class="phone">
  <div class="safe-top"></div>
  <div class="strip" style="padding-left: 2px;"><span class="iconbtn">${I.back}</span><span style="font-size: 16px; font-weight: 600;">Update BEGIA</span></div>
  <div class="page" style="gap: 10px;">
    <div class="card">
      <h2>Installed</h2>
      <div style="font-size: 15px; font-weight: 600;">BEGIA 0.9</div>
      <div class="mono muted3" style="font-size: 11.5px;">v0.9-4-g1432fd2 · shell 3 · every trial records this</div>
    </div>
    <div class="card" style="border-color: var(--confirm-border);">
      <h2>${I.wifi}From the laptop</h2>
      <div style="font-size: 15px; font-weight: 600;">BEGIA 0.10 on FAT-LAPTOP</div>
      <div class="mono muted3" style="font-size: 11.5px; margin-bottom: 10px;">192.168.0.5 · v0.10-3-g1a2b3c · 1.6 MB · needs shell 3 <span class="chip ok">OK</span></div>
      <div style="font-size: 13px; color: var(--muted); margin-bottom: 12px;">Same build the laptop is running. Recordings and settings stay where they are.</div>
      <span class="btn accent tall block">Update to 0.10</span>
    </div>
    <div class="card">
      <h2>${I.file}From a file</h2>
      <div style="font-size: 13px; color: var(--muted); margin-bottom: 12px;">Open a <span class="mono">.begia</span> file from Downloads, mail or a USB stick.</div>
      <span class="btn tall block">Choose a file</span>
    </div>
    <div class="banner confirm" style="border-radius: 8px; border: 1px solid var(--confirm-border);">If 0.10 does not start, the app goes back to 0.9 by itself and says so.</div>
    <div class="card" style="padding: 4px 4px 0;">
      <h2 style="padding: 8px 8px 0;">Kept on this phone</h2>
      ${srow(I.app, "0.9", "v0.9-4-g1432fd2 · running", `<span class="chip ok">CURRENT</span>`)}
      ${srow(I.app, "0.8.1", "v0.8.1-0-g0c03804 · previous", `<span class="btn small">Go back</span>`)}
    </div>
  </div>
  <div class="safe-bot"></div>
</div>`);

// 9. Boot ---------------------------------------------------------------------------
screens["Boot"] = wrap(`
<div class="phone" style="align-items: center; justify-content: center; gap: 16px;">
  <img src="begia-mark-lit.svg" alt="BEGIA" style="width: 174px; height: 116px;">
  <div class="word"><span class="sw-beg">BEG</span><span class="sw-ia">IA</span></div>
  <div style="height: 24px;"></div>
  <div style="width: 240px; display: flex; flex-direction: column; gap: 8px; align-items: center;">
    <div style="font-size: 14px;">Starting the recorder</div>
    <div class="bar" style="width: 100%;"><i style="width: 62%;"></i></div>
    <div class="mono muted3" style="font-size: 11.5px;">0.10 · v0.10-3-g1a2b3c · shell 3</div>
  </div>
</div>`);

// 10. Rollback -------------------------------------------------------------------------
screens["Rollback"] = wrap(`
<div class="phone" style="align-items: center; justify-content: center; gap: 16px; padding: 0 24px;">
  <img src="begia-mark.svg" alt="BEGIA" style="width: 174px; height: 116px;">
  <div class="word"><span class="sw-beg">BEG</span><span class="sw-ia">IA</span></div>
  <div style="height: 8px;"></div>
  <div class="card" style="width: 100%; border-color: var(--warn);">
    <h2 style="color: var(--warn);">${I.warn}0.10 did not start</h2>
    <div style="font-size: 14px; line-height: 1.5;">BEGIA went back to <b>0.9</b>, which started. Your recordings and settings were not touched.</div>
    <div class="mono muted3" style="font-size: 11px; margin-top: 8px;">v0.10-3-g1a2b3c · no answer on /api/state after 30 s · 11:52:14</div>
  </div>
  <span class="btn accent tall block">Continue with 0.9</span>
  <div class="row" style="width: 100%;">
    <span class="btn tall" style="flex: 1;">Show what went wrong</span>
    <span class="btn tall" style="flex: 1;">Try 0.10 again</span>
  </div>
</div>`);

// ------------------------------------------------------------------ write ----
for (const [name, html] of Object.entries(screens)) writeFileSync(`${name}.dc.html`, html);

const canvas = {
  artboards: [
    { file: "Main.dc.html", title: "Live · portrait", x: 0, y: 0, w: 411, h: 891 },
    { file: "LiveRecording.dc.html", title: "Live · recording", x: 511, y: 0, w: 411, h: 891 },
    { file: "LiveLandscape.dc.html", title: "Live · landscape, one pane", x: 1022, y: 0, w: 891, h: 411 },
    { file: "TrialBar.dc.html", title: "Trial bar · states", x: 1022, y: 531, w: 411, h: 700 },
    { file: "Signals.dc.html", title: "Signals · search first", x: 0, y: 1011, w: 411, h: 891 },
    { file: "Trials.dc.html", title: "Trials · share, send", x: 511, y: 1011, w: 411, h: 891 },
    { file: "Setup.dc.html", title: "Setup", x: 1022, y: 1360, w: 411, h: 891 },
    { file: "Update.dc.html", title: "Setup · Update", x: 1533, y: 1360, w: 411, h: 891 },
    { file: "Boot.dc.html", title: "Shell · boot", x: 0, y: 2022, w: 411, h: 891 },
    { file: "Rollback.dc.html", title: "Shell · rollback", x: 511, y: 2022, w: 411, h: 891 },
  ],
  annotations: [
    { id: "frame", x: 0, y: -170, w: 411, text: "Samsung S23: 411 × 891 dp (1080 × 2340 at 2.625×). The top 28 dp and the bottom 20 dp are left to the system, no fake status bar here.\nEvery size is the desktop token at --fs-scale 1: same panel, line, accent, rec, warn; IBM Plex Sans / IBM Plex Mono; pane, statechip, btn, chip, wsel, card, trial bar are the classes in ui/style.css." },
    { id: "layout-mode", x: 511, y: -170, w: 411, text: "Not a fork. This is a third layout mode, phone, beside classic and rail: the same DOM re-parented (placeNav already does this), the sidebar cards become the Signals, Trials and Setup tabs, the trial bar is docked and always visible.\nDigital signals are 22 dp lanes, not 96 dp charts: two lanes cost what half a pane used to." },
    { id: "gestures", x: 1453, y: 531, w: 380, text: "Touch replaces the modifier-wheel:\n• pinch = zoom time · drag = pan (steps out of live, LIVE rejoins)\n• long-press = cursor readout · double-tap = full recorded range\n• two-finger drag on an axis = offset that axis\n• tap a pane = landscape, one pane full screen; swipe = next pane\n• space / M / L / F keys become Start, Mark, LIVE, and the tap-to-maximize" },
    { id: "workflow", x: 1963, y: 0, w: 340, text: "The FAT day on a phone, in order of how often it happens:\n1 glance at live values\n2 Start · Mark · Stop, without navigating anywhere\n3 open a trial, share the CSV or the report, or send it to the laptop\n4 add a signal: search first, the tree is the fallback\n5 rarely: pick the furnace, the set, update the app\nAnalyse waits for a tablet width." },
    { id: "update", x: 2044, y: 1360, w: 340, text: "Update without reinstalling: the shell (APK) holds Python and the wheels; the payload (.begia) holds app/, ui/, presets.json and a manifest with the honest git build stamp.\nThree ways in: the laptop's BEGIA over WiFi, a file from anywhere, adb from the dev PC.\nA payload that does not answer /api/state in 30 s is rolled back to the previous slot, and the screen says so." },
    { id: "open", x: 2044, y: 1700, w: 340, text: "Open for you to settle on the canvas:\n• the window chip: tap-to-pick (30 s / 1 min / 5 min / 10 min) or pinch only?\n• quick-mark chips: three fixed, or the desktop's nine keys?\n• Stop: plain tap, or hold-to-stop so a pocket cannot end a trial?\n• the second-screen mode lives in Setup today; a switch on the boot screen instead?" },
  ],
  launch: { view: "canvas" },
};
writeFileSync("canvas.json", JSON.stringify(canvas, null, 2));
console.log("wrote", Object.keys(screens).length, "artboards + canvas.json");
