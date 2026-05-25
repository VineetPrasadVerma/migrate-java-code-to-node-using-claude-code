/**
 * Unit tests for orderService.processOrder()
 *
 * child_process.execFile is mocked so no real Java process is spawned.
 * All test data values are taken from ai-context/TEST_CASES.md.
 */
import { jest } from '@jest/globals';

// ── Mock setup (must happen before dynamic imports) ──────────────────────────

const mockStdin = {
  write: jest.fn(),
  end:   jest.fn(),
  on:    jest.fn(),
};

const mockExecFile = jest.fn();

jest.unstable_mockModule('child_process', () => ({
  execFile: mockExecFile,
}));

// Silence logger output during tests
jest.unstable_mockModule('../../src/utils/logger.js', () => ({
  default: { info: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));

// ── Dynamic imports (after mocks are registered) ─────────────────────────────

const { processOrder }      = await import('../../src/services/orderService.js');
const { JavaProcessError }  = await import('../../src/middleware/errorHandler.js');

// ── Fixtures (from TEST_CASES.md) ─────────────────────────────────────────────

const ORDER_1_REQUEST = {
  orderId:                'ORD-001',
  customerId:             'C002',
  items: [
    { productId: 'P001', quantity: 1 },
    { productId: 'P002', quantity: 3 },
  ],
  promoCode:               'SAVE10',
  shippingZone:            2,
  shippingType:            'STANDARD',
  allowPartialFulfillment: false,
  orderDate:               '2026-05-20',
};

const ORDER_1_RESPONSE = {
  orderId:       'ORD-001',
  status:        'PROCESSED',
  subtotal:      1120.5,
  totalTax:      201.69,
  promoCode:     'SAVE10',
  promoDiscount: 112.05,
  shippingCost:  8.0,
  grandTotal:    1218.14,
  fraudFlags:    [],
  warnings:      [],
  errors:        [],
  message:       'Order processed successfully',
};

const ORDER_2_REQUEST = {
  orderId:                'ORD-002',
  customerId:             'C005',
  items: [{ productId: 'P001', quantity: 2 }],
  shippingZone:            1,
  shippingType:            'STANDARD',
  allowPartialFulfillment: false,
  orderDate:               '2026-05-20',
};

const ORDER_2_RESPONSE = {
  orderId:    'ORD-002',
  status:     'REJECTED',
  subtotal:   2400.0,
  totalTax:   432.0,
  grandTotal: 2837.0,
  fraudFlags: [{
    flag:     'HIGH_VALUE_NEW_CUSTOMER',
    severity: 'HIGH',
    description: 'Order total $2837.00 exceeds $1000.00 threshold for new accounts (account age: 25 days, threshold: 90 days)',
  }],
  warnings: [],
  errors:   [],
  message:  'Order rejected due to high-severity fraud detection',
};

// ── Helper ─────────────────────────────────────────────────────────────────────

/**
 * Configures mockExecFile to call its callback on the next event-loop tick,
 * simulating the async behaviour of a real child process.
 */
function setupJavaMock({ stdout = '', stderr = '', error = null } = {}) {
  mockExecFile.mockImplementationOnce((_cmd, _args, _opts, callback) => {
    process.nextTick(() => callback(error, stdout, stderr));
    return { stdin: mockStdin };
  });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  jest.clearAllMocks();
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 1: successful PROCESSED response
// ─────────────────────────────────────────────────────────────────────────────
describe('processOrder — PROCESSED (Order 1 happy path)', () => {
  test('spawns Java JAR with the correct binary and -jar flag', async () => {
    setupJavaMock({ stdout: JSON.stringify(ORDER_1_RESPONSE) });

    await processOrder(ORDER_1_REQUEST);

    expect(mockExecFile).toHaveBeenCalledTimes(1);
    const [javaBin, args] = mockExecFile.mock.calls[0];
    expect(typeof javaBin).toBe('string');  // JAVA_BIN resolved
    expect(args[0]).toBe('-jar');
    expect(args[1]).toMatch(/order-processing-system.*\.jar$/);
  });

  test('writes the order request as JSON to stdin and closes it', async () => {
    setupJavaMock({ stdout: JSON.stringify(ORDER_1_RESPONSE) });

    await processOrder(ORDER_1_REQUEST);

    expect(mockStdin.write).toHaveBeenCalledWith(JSON.stringify(ORDER_1_REQUEST));
    expect(mockStdin.end).toHaveBeenCalledTimes(1);
  });

  test('registers an error handler on stdin', async () => {
    setupJavaMock({ stdout: JSON.stringify(ORDER_1_RESPONSE) });

    await processOrder(ORDER_1_REQUEST);

    expect(mockStdin.on).toHaveBeenCalledWith('error', expect.any(Function));
  });

  test('returns the parsed Java response unchanged', async () => {
    setupJavaMock({ stdout: JSON.stringify(ORDER_1_RESPONSE) });

    const result = await processOrder(ORDER_1_REQUEST);

    expect(result).toEqual(ORDER_1_RESPONSE);
  });

  test('grandTotal is exactly 1218.14 (Order 1 sanity check)', async () => {
    setupJavaMock({ stdout: JSON.stringify(ORDER_1_RESPONSE) });

    const result = await processOrder(ORDER_1_REQUEST);

    expect(result.grandTotal).toBe(1218.14);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 2: REJECTED outcome is returned as-is — not thrown as an error
// ─────────────────────────────────────────────────────────────────────────────
describe('processOrder — REJECTED (Order 2 fraud rejection)', () => {
  test('returns the REJECTED result without throwing', async () => {
    setupJavaMock({ stdout: JSON.stringify(ORDER_2_RESPONSE) });

    const result = await processOrder(ORDER_2_REQUEST);

    expect(result.status).toBe('REJECTED');
  });

  test('REJECTED result contains the HIGH_VALUE_NEW_CUSTOMER fraud flag', async () => {
    setupJavaMock({ stdout: JSON.stringify(ORDER_2_RESPONSE) });

    const result = await processOrder(ORDER_2_REQUEST);

    expect(result.fraudFlags).toHaveLength(1);
    expect(result.fraudFlags[0].flag).toBe('HIGH_VALUE_NEW_CUSTOMER');
    expect(result.fraudFlags[0].severity).toBe('HIGH');
  });

  test('REJECTED result grandTotal is exactly 2837.00', async () => {
    setupJavaMock({ stdout: JSON.stringify(ORDER_2_RESPONSE) });

    const result = await processOrder(ORDER_2_REQUEST);

    expect(result.grandTotal).toBe(2837.0);
  });

  test('does NOT throw even though status is REJECTED', async () => {
    setupJavaMock({ stdout: JSON.stringify(ORDER_2_RESPONSE) });

    await expect(processOrder(ORDER_2_REQUEST)).resolves.not.toThrow();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 3: Java process failure → throws JavaProcessError
// ─────────────────────────────────────────────────────────────────────────────
describe('processOrder — Java process failures', () => {
  test('throws JavaProcessError when Java exits non-zero', async () => {
    const javaError = Object.assign(new Error('Command failed'), { code: 1, killed: false });
    setupJavaMock({ error: javaError, stderr: 'Exception in thread "main" java.lang.RuntimeException' });

    await expect(processOrder(ORDER_1_REQUEST)).rejects.toBeInstanceOf(JavaProcessError);
  });

  test('error message includes the exit code', async () => {
    const javaError = Object.assign(new Error('Command failed'), { code: 1, killed: false });
    setupJavaMock({ error: javaError, stderr: 'some stderr' });

    await expect(processOrder(ORDER_1_REQUEST)).rejects.toThrow(/exited with code 1/);
  });

  test('throws JavaProcessError when Java process times out (error.killed = true)', async () => {
    const timedOutError = Object.assign(new Error('killed'), { code: null, killed: true });
    setupJavaMock({ error: timedOutError });

    await expect(processOrder(ORDER_1_REQUEST)).rejects.toBeInstanceOf(JavaProcessError);
  });

  test('timeout error message mentions the timeout duration', async () => {
    const timedOutError = Object.assign(new Error('killed'), { code: null, killed: true });
    setupJavaMock({ error: timedOutError });

    await expect(processOrder(ORDER_1_REQUEST)).rejects.toThrow(/timed out/i);
  });

  test('throws JavaProcessError when Java stdout is not valid JSON', async () => {
    setupJavaMock({ stdout: 'not valid json {{{' });

    await expect(processOrder(ORDER_1_REQUEST)).rejects.toBeInstanceOf(JavaProcessError);
  });

  test('parse-error message mentions failed to parse', async () => {
    setupJavaMock({ stdout: '' });

    await expect(processOrder(ORDER_1_REQUEST)).rejects.toThrow(/parse/i);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 4: missing required fields → throws before spawning Java
// ─────────────────────────────────────────────────────────────────────────────
describe('processOrder — missing required fields', () => {
  test('throws when orderId is missing', async () => {
    const { orderId: _omit, ...noOrderId } = ORDER_1_REQUEST;

    await expect(processOrder(noOrderId)).rejects.toThrow(/orderId/);
  });

  test('does NOT spawn Java when orderId is missing', async () => {
    const { orderId: _omit, ...noOrderId } = ORDER_1_REQUEST;

    await expect(processOrder(noOrderId)).rejects.toThrow();
    expect(mockExecFile).not.toHaveBeenCalled();
  });

  test('throws when customerId is missing', async () => {
    const { customerId: _omit, ...noCustomer } = ORDER_1_REQUEST;

    await expect(processOrder(noCustomer)).rejects.toThrow(/customerId/);
  });

  test('throws when items is missing', async () => {
    const { items: _omit, ...noItems } = ORDER_1_REQUEST;

    await expect(processOrder(noItems)).rejects.toThrow(/items/);
  });

  test('throws when shippingZone is missing', async () => {
    const { shippingZone: _omit, ...noZone } = ORDER_1_REQUEST;

    await expect(processOrder(noZone)).rejects.toThrow(/shippingZone/);
  });

  test('throws when shippingType is missing', async () => {
    const { shippingType: _omit, ...noType } = ORDER_1_REQUEST;

    await expect(processOrder(noType)).rejects.toThrow(/shippingType/);
  });

  test('reports all missing fields in a single error', async () => {
    const minimal = { orderId: 'X' };  // missing customerId, items, shippingZone, shippingType

    await expect(processOrder(minimal)).rejects.toThrow(
      /customerId.*items.*shippingZone.*shippingType|Missing required fields/
    );
  });
});
