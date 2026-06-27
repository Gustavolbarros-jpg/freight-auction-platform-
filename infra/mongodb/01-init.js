db = db.getSiblingDB('audit_db');

db.createUser({
  user: 'audit_user',
  pwd: 'audit_pass',
  roles: [{ role: 'readWrite', db: 'audit_db' }]
});

db.createCollection('events', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['type', 'service', 'timestamp', 'payload'],
      properties: {
        type:          { bsonType: 'string', enum: ['BID_RECEIVED', 'BID_VALIDATED', 'BID_REJECTED', 'AUCTION_OPENED', 'AUCTION_CLOSED'] },
        service:       { bsonType: 'string' },
        timestamp:     { bsonType: 'date' },
        payload:       { bsonType: 'object' },
        auctionId:     { bsonType: 'string' },
        userId:        { bsonType: 'string' },
        correlationId: { bsonType: 'string' }
      }
    }
  }
});

db.events.createIndex({ timestamp: -1 });
db.events.createIndex({ auctionId: 1, timestamp: -1 });
db.events.createIndex({ userId: 1, timestamp: -1 });
db.events.createIndex({ type: 1 });
db.events.createIndex({ correlationId: 1 });