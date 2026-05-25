/**
 * /verify-parity — two-way parity check
 *
 * For each of the 5 test inputs:
 *   1. Run Java CLI (stdin → stdout)
 *   2. POST to Node.js HTTP API
 *   3. Compare: status, grandTotal, subtotal, totalTax, promoDiscount,
 *               shippingCost, promoCode, fraudFlags[].flag+severity, errors[]
 *
 * Usage: node parity_check.mjs          (all 5 orders)
 *        node parity_check.mjs order1   (single order by name prefix)
 */
import { execFile }  from 'child_process';
import { readFile }  from 'fs/promises';

const BASE   = '/Users/vineetverma/Desktop/Velotio/Medispend/POC';
const JAR    = `${BASE}/java-order-system/target/order-processing-system-1.0.0-jar-with-dependencies.jar`;
const INPUTS = `${BASE}/java-order-system/test-inputs`;
const JAVA   = process.env.JAVA_BIN ?? '/opt/homebrew/opt/openjdk@11/bin/java';
const PORT   = process.env.PORT ?? 3000;

const ALL_ORDERS = [
  'order1_happy_path',
  'order2_fraud_rejection',
  'order3_inventory_shortage',
  'order4_platinum_freeship',
  'order5_fraud_warnings',
];

const SCALAR_FIELDS = [
  'status', 'grandTotal', 'subtotal', 'totalTax',
  'promoDiscount', 'shippingCost', 'promoCode',
];

// ── helpers ───────────────────────────────────────────────────────────────────

function round2(v) {
  if (v == null) return null;
  return typeof v === 'number' ? Math.round(v * 100) / 100 : v;
}

function runJar(inputJson) {
  return new Promise((resolve, reject) => {
    const proc = execFile(
      JAVA, ['-jar', JAR], { timeout: 15_000 },
      (err, stdout, stderr) => {
        if (err) return reject(new Error(`Java exit ${err.code}: ${stderr || err.message}`));
        try { resolve(JSON.parse(stdout)); }
        catch (e) { reject(new Error(`Java stdout not JSON: ${stdout.slice(0, 200)}`)); }
      }
    );
    proc.stdin.write(inputJson);
    proc.stdin.end();
  });
}

async function callNodeApi(inputJson) {
  const res = await fetch(`http://127.0.0.1:${PORT}/api/orders`, {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    inputJson,
  });
  return res.json();
}

function diffResults(jResult, nResult) {
  const diffs = [];

  for (const field of SCALAR_FIELDS) {
    const jv = round2(jResult[field]);
    const nv = round2(nResult[field]);
    if (JSON.stringify(jv) !== JSON.stringify(nv))
      diffs.push({ field, java: jv, node: nv });
  }

  // fraudFlags — compare by flag+severity key
  const toFlagKey = f => `${f.flag}|${f.severity}`;
  const jff = new Set((jResult.fraudFlags ?? []).map(toFlagKey));
  const nff = new Set((nResult.fraudFlags ?? []).map(toFlagKey));
  const ffJonly = [...jff].filter(k => !nff.has(k));
  const ffNonly = [...nff].filter(k => !jff.has(k));
  if (ffJonly.length || ffNonly.length)
    diffs.push({ field: 'fraudFlags', java: ffJonly, node: ffNonly });

  // errors[]
  const je = new Set(jResult.errors ?? []);
  const ne = new Set(nResult.errors ?? []);
  const ejOnly = [...je].filter(k => !ne.has(k));
  const enOnly = [...ne].filter(k => !je.has(k));
  if (ejOnly.length || enOnly.length)
    diffs.push({ field: 'errors', java: ejOnly, node: enOnly });

  return diffs;
}

// ── main ──────────────────────────────────────────────────────────────────────

const filter   = process.argv[2];
const orders   = filter
  ? ALL_ORDERS.filter(n => n.includes(filter))
  : ALL_ORDERS;

let allPass = true;

for (const name of orders) {
  const inputJson = await readFile(`${INPUTS}/${name}.json`, 'utf8');

  console.log(`\n────────────────────────────────────────`);
  console.log(`Order: ${name}`);

  let jResult, nResult;
  try {
    [jResult, nResult] = await Promise.all([
      runJar(inputJson),
      callNodeApi(inputJson),
    ]);
  } catch (err) {
    console.log(`  ❌ ERROR: ${err.message}`);
    allPass = false;
    continue;
  }

  const diffs = diffResults(jResult, nResult);

  if (diffs.length === 0) {
    const flags = (jResult.fraudFlags ?? []).map(f => f.flag).join(',') || 'none';
    console.log(`  ✅ PARITY VERIFIED`);
    console.log(`     status=${jResult.status}  grandTotal=${jResult.grandTotal}  fraudFlags=${flags}`);
  } else {
    allPass = false;
    console.log(`  ❌ PARITY FAILED — ${diffs.length} difference(s):`);
    for (const d of diffs) {
      console.log(`     field:    ${d.field}`);
      console.log(`     java:     ${JSON.stringify(d.java)}`);
      console.log(`     node api: ${JSON.stringify(d.node)}`);
    }
  }
}

console.log(`\n════════════════════════════════════════`);
if (allPass) {
  console.log(`✅ ALL ${orders.length} ORDER(S): PARITY VERIFIED`);
} else {
  console.log(`❌ PARITY FAILURES DETECTED — see above`);
}
process.exit(allPass ? 0 : 1);
