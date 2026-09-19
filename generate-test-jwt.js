const jwt = require('jsonwebtoken');

const secret = process.env.JWT_SECRET;
if (!secret) {
  console.error('Error: JWT_SECRET environment variable is required.');
  console.error('Usage: JWT_SECRET=your-secret-node generate-test-jwt.js');
  process.exit(1);
}

const payload = {
  sub: 'test-user-001',
  userId: 'test-user-001',
  email: 'test@example.com',
  firstName: 'Test',
  lastName: 'User',
  iat: Math.floor(Date.now() / 1000),
  exp: Math.floor(Date.now() / 1000) + 3600 // 1 hour
};

const token = jwt.sign(payload, secret, { algorithm: 'HS256' });
console.log(token);