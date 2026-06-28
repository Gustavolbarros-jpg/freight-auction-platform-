"""
Testes de integração dos endpoints do analytics-service.
Usa mocks das funções de banco — valida contratos HTTP e estrutura das respostas.
"""


def test_health_retorna_200(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["service"] == "analytics-service"


def test_auctions_retorna_200_com_campos_esperados(client):
    response = client.get("/v1/analytics/auctions")
    assert response.status_code == 200
    data = response.json()
    assert "total" in data
    assert "by_status" in data
    assert "closed_with_winner" in data
    assert "recent_events" in data


def test_auctions_total_e_numerico(client):
    response = client.get("/v1/analytics/auctions")
    assert response.status_code == 200
    assert isinstance(response.json()["total"], int)


def test_bids_retorna_200_com_campos_esperados(client):
    response = client.get("/v1/analytics/bids")
    assert response.status_code == 200
    data = response.json()
    assert "total" in data
    assert "by_status" in data
    assert "amounts" in data
    assert "recent_events" in data


def test_bids_amounts_tem_min_max_avg(client):
    response = client.get("/v1/analytics/bids")
    assert response.status_code == 200
    amounts = response.json()["amounts"]
    assert "min_amount" in amounts
    assert "max_amount" in amounts
    assert "avg_amount" in amounts


def test_carriers_retorna_200_com_ranking(client):
    response = client.get("/v1/analytics/carriers")
    assert response.status_code == 200
    data = response.json()
    assert "ranking" in data
    assert "recent_events" in data


def test_carriers_ranking_tem_campos_esperados(client):
    response = client.get("/v1/analytics/carriers")
    assert response.status_code == 200
    ranking = response.json()["ranking"]
    assert len(ranking) > 0
    carrier = ranking[0]
    assert "user_id" in carrier
    assert "total_bids" in carrier
    assert "validated_bids" in carrier
    assert "rejected_bids" in carrier