import { randomUUID } from 'crypto';
import logger from '../utils/logger.js';

export default function requestLogger(req, res, next) {
  req.requestId = req.headers['x-request-id'] ?? randomUUID();

  const startMs = Date.now();

  res.on('finish', () => {
    logger.info('request completed', {
      requestId:  req.requestId,
      method:     req.method,
      path:       req.path,
      statusCode: res.statusCode,
      durationMs: Date.now() - startMs,
    });
  });

  next();
}
