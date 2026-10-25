// Runs the web prototype's logic headless and writes reference outputs for the Kotlin engine tests.
// Usage (from android/): node engine/tools/golden.js
// Output: engine/src/test/resources/golden.json.gz
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '../../..');
const html = fs.readFileSync(path.join(root, 'prototype/reppal-prototype.html'), 'utf8');
const scripts = [...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].map(m => m[1]);
const proto = scripts[scripts.length - 1];

const anyEl = new Proxy({}, { get: (t, k) => (k in t ? t[k] : (k === 'classList' ? { toggle() {} } : () => {})), set: (t, k, v) => { t[k] = v; return true; } });
const ctx = {
  document: { querySelector: () => anyEl, documentElement: anyEl },
  localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
  window: { scrollTo() {} },
  location: { reload() {} },
  console,
  OUT: null,
};
vm.createContext(ctx);

const harness = `
;(function(){
let seed = 12345;
const rnd = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; };
const ri = (lo, hi) => lo + Math.floor(rnd() * (hi - lo + 1));
const pickOne = a => a[Math.floor(rnd() * a.length)];
const out = { weeks: [], progress: [], adjust: [], parse: [], derive: [], level: [] };

const benches = ['none', 'flat', 'adj'];
const dbSets = [[], [5], [2, 4, 8], [10, 20], [...ALLW]];
const splits = ['auto', 'full', 'ul', 'ppl', 'bro', 'custom', 'custom2'];
const levels = ['beginner', 'intermediate', 'advanced'];
const concerns = ['', 'lower back pain', 'bad knees, cannot do push-ups', 'shoulder'];
const CUSTOM2 = [['abs','chest','biceps'],['glutes','quads','hams','abs'],[],['chest'],['chest'],['lats','shoulders','reardelts','triceps','biceps','abs'],['quads']];

// Compact day encoding: "label|slot:id<base@w;...|miss,miss"
const dayOut = b => b.lab + '|' + b.items.map(i => i.p + ':' + i.id + '<' + i.base + '@' + st(i.id).w).join(';') + '|' + b.miss.join(',');

for (const bench of benches) for (const w of dbSets) for (let days = 1; days <= 7; days++)
for (const split of splits) for (const lvl of levels) for (const concern of concerns) {
  if (concern !== '' && lvl !== 'beginner') continue;
  S.bench = bench; S.w = [...w]; S.days = days; S.lvl = lvl; S.ex = {}; S.sw = {};
  S.split = split === 'custom2' ? 'custom' : split;
  S.custom = split === 'custom2' ? CUSTOM2.map(a => a.slice()) : null;
  S.skip = derive(concern);
  const seq = wkSeq();
  const week = seq.map(l => build(l));
  const alts = week.map(b => b.items.map(i => swAlts(i, b).join(',')).join(';'));
  const rec = { bench, w, days, split, lvl, concern, seq, days_: week.map(dayOut), alts };
  if (concern === '') {
    const sw = {};
    week.forEach(b => { const a = b.items.length ? swAlts(b.items[0], b) : []; if (a.length) sw[b.items[0].id] = a[0]; });
    S.sw = sw;
    rec.swaps = sw;
    rec.swapped = seq.map(l => dayOut(build(l)));
  }
  out.weeks.push(rec);
}

// Next-session progression
const ids = Object.keys(EX);
for (let n = 0; n < 3000; n++) {
  const id = pickOne(ids), e = EX[id];
  S.w = pickOne([[5], [2, 4, 8], [4, 5, 6, 8, 10, 12], [...ALLW], [10, 20]]);
  S.bench = 'adj'; S.lvl = 'beginner';
  const a = ws(), hi = e.bw ? 15 : 12;
  const p = { id, sets: ri(2, 4), target: ri(6, hi), w: e.bw ? 0 : pickOne(a) };
  const reps = [], sl = [];
  for (let i = 0; i < p.sets + (rnd() < 0.15 ? -1 : 0); i++) {
    const wi = e.bw ? 0 : (rnd() < 0.7 ? p.w : pickOne(a));
    const tgt = rnd() < 0.7 ? p.target : ri(6, hi);
    reps.push(Math.max(1, p.target + ri(-5, 4)));
    sl.push({ w: wi, target: tgt });
  }
  if (!reps.length) continue;
  S.ex = {}; S.ex[id] = { w: p.w, target: p.target, sets: p.sets };
  const r = progress(id, reps, sl, p);
  out.progress.push({ id, owned: a, plan: p, reps, sl, next: { ...S.ex[id] }, m: r.m, up: r.up });
}

// Live adjustment
for (let n = 0; n < 3000; n++) {
  const id = pickOne(ids), e = EX[id];
  S.w = pickOne([[5], [2, 4, 8], [4, 5, 6, 8, 10, 12], [...ALLW], [24]]);
  const a = ws(), hi = e.bw ? 15 : 12;
  const cp = { w: e.bw ? 0 : pickOne(a), target: ri(6, hi) };
  const pv = { reps: Math.max(1, cp.target + ri(-3, 2)), weight_kg: e.bw ? null : (rnd() < 0.7 ? null : pickOne(a)), effort: pickOne(['easy', 'easy', 'just right', 'hard']), flags: rnd() < 0.1 ? ['pain'] : [] };
  S.c = {};
  const m = adjust(id, pv, cp);
  out.adjust.push({ id, owned: a, cp, pv, next: S.c.live[id], m });
}

const texts = [...EXAMPLES, '12', 'twelve reps', 'Ten reps at 8 kg', '8 kilo, 10 reps, easy', 'fourteen easy', 'did 2.5 kg for 9 reps',
  'struggled with the last one, 7', 'felt a twinge in my knee. 10 reps', 'just right', 'Couldn\\'t finish, 6 reps; elbow ache', 'easy peasy 15',
  'three sets of ten', '11 reps, heavy, light-headed', 'breezy 13 reps', 'hurt my wrist', '10 reps at 12kg hard', 'Seven, tough, form broke down'];
texts.forEach(t => out.parse.push({ t, r: parse(t) }));

const ctexts = ['', 'lower back pain', 'bad knees, cannot do push-ups', 'shoulder', 'I have a disc problem and can\\'t do deadlifts',
  'tennis elbow', 'wrists hurt on push ups', 'no lunges please', 'bad hip', 'neck', 'sciatica and knee surgery last year', 'NO ROWS', 'rows', 'Bench Press hurts',
  'can\\'t do flies or the dead bug', 'single leg balance is bad', 'Hamstring curl cramps', 'bicep tendon', 'tricep', 'skull crushers', 'mountain climbers make my wrist hurt'];
ctexts.forEach(t => out.derive.push({ t, ids: derive(t) }));

const ltexts = ['', 'never lifted', '2 years', '3 yrs of training', '8 months', '5 months', 'intermediate', 'advanced lifter', 'took a break after 5 years',
  '1.5 years', '24 months', 'I am a beginner', 'returning after 3 years', '6 months', 'lifting since college'];
ltexts.forEach(t => out.level.push({ t, lvl: lvl(t) }));

OUT = JSON.stringify(out);
})();
`;
vm.runInContext(proto + harness, ctx);
const dest = path.join(__dirname, '../src/test/resources/golden.json.gz');
fs.writeFileSync(dest, require('zlib').gzipSync(ctx.OUT, { level: 9 }));
console.log('wrote', dest, ctx.OUT.length, 'bytes');
