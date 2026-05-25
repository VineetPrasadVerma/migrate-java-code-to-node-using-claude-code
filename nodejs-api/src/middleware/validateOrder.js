import Joi from 'joi';

const orderSchema = Joi.object({
  orderId: Joi.string().required(),

  customerId: Joi.string().required(),

  items: Joi.array()
    .items(
      Joi.object({
        productId: Joi.string().required(),
        quantity:  Joi.number().integer().min(1).max(100).required(),
      })
    )
    .min(1)
    .required(),

  promoCode: Joi.string().allow(null, '').optional(),

  shippingZone: Joi.number().integer().min(1).max(5).required(),

  shippingType: Joi.string().valid('STANDARD', 'EXPRESS', 'OVERNIGHT').required(),

  allowPartialFulfillment: Joi.boolean().default(false),

  orderDate: Joi.string()
    .pattern(/^\d{4}-\d{2}-\d{2}$/, 'YYYY-MM-DD')
    .optional(),
});

export default function validateOrder(req, res, next) {
  const { error, value } = orderSchema.validate(req.body, {
    abortEarly: false,
    stripUnknown: true,
  });

  if (error) {
    const details = error.details.map(d => `${d.path.join('.')}: ${d.message}`);
    return res.status(400).json({ error: 'Invalid request', details });
  }

  req.body = value;
  next();
}
