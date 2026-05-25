import { execFile } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import logger from '../utils/logger.js';
import { JavaProcessError } from '../middleware/errorHandler.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const JAR_PATH = process.env.JAR_PATH
  ? path.resolve(process.env.JAR_PATH)
  : path.resolve(__dirname, '../../../java-order-system/target/order-processing-system-1.0.0-jar-with-dependencies.jar');

const JAVA_BIN    = process.env.JAVA_BIN || 'java';
const TIMEOUT_MS  = 15_000;

const REQUIRED_FIELDS = ['orderId', 'customerId', 'items', 'shippingZone', 'shippingType'];

function assertRequiredFields(orderRequest) {
  const missing = REQUIRED_FIELDS.filter(
    f => orderRequest[f] == null || orderRequest[f] === ''
  );
  if (missing.length > 0) {
    throw new Error(`Missing required fields: ${missing.join(', ')}`);
  }
}

/**
 * Processes an order by invoking the Java JAR via stdin/stdout.
 * This is a thin wrapper — all business logic lives in Java.
 * A REJECTED status is a valid outcome and is returned as-is.
 *
 * @param {Object} orderRequest - The validated order payload
 * @returns {Promise<Object>} OrderResult from the Java JAR (PROCESSED or REJECTED)
 * @throws {JavaProcessError} When the Java process times out, exits non-zero, or returns unparseable output
 * @throws {Error} When required fields are missing from orderRequest
 */
export async function processOrder(orderRequest) {
  assertRequiredFields(orderRequest);

  const input = JSON.stringify(orderRequest);

  logger.info('invoking Java JAR', {
    orderId:  orderRequest.orderId,
    jar:      JAR_PATH,
    javaBin:  JAVA_BIN,
  });

  return new Promise((resolve, reject) => {
    const proc = execFile(
      JAVA_BIN,
      ['-jar', JAR_PATH],
      { timeout: TIMEOUT_MS },
      (error, stdout, stderr) => {
        if (error) {
          if (error.killed) {
            logger.error('Java process timed out', { orderId: orderRequest.orderId, stderr });
            return reject(new JavaProcessError(
              `Java process timed out after ${TIMEOUT_MS}ms`,
              { exitCode: null, stderr }
            ));
          }
          logger.error('Java process exited with error', {
            orderId:  orderRequest.orderId,
            exitCode: error.code,
            stderr,
          });
          return reject(new JavaProcessError(
            `Java process exited with code ${error.code}: ${stderr || error.message}`,
            { exitCode: error.code, stderr }
          ));
        }

        let result;
        try {
          result = JSON.parse(stdout);
        } catch (parseError) {
          logger.error('Failed to parse Java output', {
            orderId: orderRequest.orderId,
            stdout:  stdout.slice(0, 500),
            stderr,
          });
          return reject(new JavaProcessError(
            `Failed to parse Java output: ${parseError.message}`,
            { exitCode: 0, stderr }
          ));
        }

        // REJECTED is a valid business outcome — not an error
        logger.info('Java JAR responded', {
          orderId:    orderRequest.orderId,
          status:     result.status,
          grandTotal: result.grandTotal,
        });

        resolve(result);
      }
    );

    proc.stdin.on('error', err => {
      // stdin pipe closed before we finished writing (e.g. Java crashed immediately)
      reject(new JavaProcessError(
        `Failed to write to Java process stdin: ${err.message}`,
        { exitCode: null, stderr: '' }
      ));
    });

    proc.stdin.write(input);
    proc.stdin.end();
  });
}
