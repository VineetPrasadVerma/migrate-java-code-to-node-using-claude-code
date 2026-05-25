import Joi from 'joi';
import logger from '../utils/logger.js';

/**
 * Thrown by orderService when the Java subprocess exits non-zero
 * or cannot be spawned. Import this wherever Java errors need raising.
 */
export class JavaProcessError extends Error {
  constructor(message, { exitCode, stderr } = {}) {
    super(message);
    this.name = 'JavaProcessError';
    this.exitCode = exitCode;
    this.stderr = stderr;
  }
}

// eslint-disable-next-line no-unused-vars
export default function errorHandler(err, req, res, next) {
  logger.error(err.message, {
    stack:  err.stack,
    path:   req.path,
    method: req.method,
    // request body intentionally omitted — may contain PII
  });

  if (err instanceof JavaProcessError) {
    return res.status(502).json({
      error:     'Order processing service unavailable',
      retryable: true,
    });
  }

  if (err instanceof Joi.ValidationError) {
    const details = err.details.map(d => `${d.path.join('.')}: ${d.message}`);
    return res.status(400).json({
      error:   'Invalid request',
      details,
    });
  }

  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    return res.status(400).json({
      error: 'Invalid JSON',
    });
  }

  return res.status(500).json({
    error:     'Internal server error',
    retryable: false,
  });
}
