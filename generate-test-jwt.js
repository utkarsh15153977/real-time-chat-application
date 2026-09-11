const jwt = require('jsonwebtoken');

const secret = 'blinkChatSuperSecretKeyForJwtTokenSigning2024MustBeLongEnough!!';

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