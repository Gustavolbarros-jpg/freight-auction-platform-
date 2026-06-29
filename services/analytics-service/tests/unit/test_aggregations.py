from datetime import datetime
from decimal import Decimal

from bson import Decimal128, ObjectId

from app.database import serialize_mongo_value, serialize_mongo_document


# -------------------------------------------------------------------------
# serialize_mongo_value
# -------------------------------------------------------------------------

def test_serializa_objectid_para_string():
    oid = ObjectId()
    result = serialize_mongo_value(oid)
    assert isinstance(result, str)
    assert result == str(oid)


def test_serializa_datetime_para_iso():
    dt = datetime(2026, 6, 27, 10, 30, 0)
    result = serialize_mongo_value(dt)
    assert result == "2026-06-27T10:30:00"


def test_serializa_decimal128_para_string():
    d = Decimal128("750.50")
    result = serialize_mongo_value(d)
    assert result == "750.50"


def test_serializa_decimal_para_string():
    d = Decimal("900.00")
    result = serialize_mongo_value(d)
    assert result == "900.00"


def test_serializa_dict_recursivamente():
    doc = {"amount": Decimal("500.00"), "id": ObjectId()}
    result = serialize_mongo_value(doc)
    assert isinstance(result["amount"], str)
    assert isinstance(result["id"], str)


def test_serializa_lista_recursivamente():
    items = [Decimal128("100.00"), Decimal128("200.00")]
    result = serialize_mongo_value(items)
    assert result == ["100.00", "200.00"]


def test_valor_primitivo_passa_sem_alteracao():
    assert serialize_mongo_value("texto") == "texto"
    assert serialize_mongo_value(42) == 42
    assert serialize_mongo_value(None) is None


# -------------------------------------------------------------------------
# serialize_mongo_document
# -------------------------------------------------------------------------

def test_serializa_documento_completo():
    doc = {
        "_id": ObjectId(),
        "type": "BID_VALIDATED",
        "auctionId": "11111111-1111-1111-1111-111111111111",
        "amount": Decimal128("750.00"),
        "timestamp": datetime(2026, 6, 27, 12, 0, 0)
    }

    result = serialize_mongo_document(doc)

    assert isinstance(result["_id"], str)
    assert result["type"] == "BID_VALIDATED"
    assert result["amount"] == "750.00"
    assert result["timestamp"] == "2026-06-27T12:00:00"


def test_documento_sem_tipos_especiais_permanece_igual():
    doc = {"status": "OPEN", "total": 5}
    result = serialize_mongo_document(doc)
    assert result == doc