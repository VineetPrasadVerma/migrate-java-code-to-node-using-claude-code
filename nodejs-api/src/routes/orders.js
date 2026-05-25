import { Router } from 'express';
import validateOrder from '../middleware/validateOrder.js';
import { processOrder } from '../services/orderService.js';
import logger from '../utils/logger.js';

const router = Router();

router.post('/', validateOrder, async (req, res, next) => {
  const { orderId } = req.body;
  const startMs = Date.now();

  try {
    const result = await processOrder(req.body);

    const durationMs = Date.now() - startMs;

    logger.info('order processed', {
      orderId,
      status:     result.status,
      grandTotal: result.grandTotal,
      durationMs,
    });

    const statusCode = result.status === 'PROCESSED' ? 200
                     : result.status === 'REJECTED'  ? 422
                     : 200;

    return res.status(statusCode).json(result);
  } catch (err) {
    logger.error('order processing failed', {
      orderId,
      error: err.message,
    });
    next(err);
  }
});

export default router;
