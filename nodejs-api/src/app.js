import express from 'express';
import requestLogger from './middleware/requestLogger.js';
import errorHandler from './middleware/errorHandler.js';
import ordersRouter from './routes/orders.js';

const app = express();

app.use(express.json());
app.use(requestLogger);

app.use('/api/orders', ordersRouter);

app.use(errorHandler);

export default app;
