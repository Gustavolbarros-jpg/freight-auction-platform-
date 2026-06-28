#!/bin/bash
set -e

echo "==============================="
echo " Testes de carga k6"
echo "==============================="

SCENARIOS_DIR="$(dirname "$0")/scenarios"

echo ""
echo ">>> [1/4] Smoke test"
k6 run "$SCENARIOS_DIR/smoke.js"

echo ""
echo ">>> [2/4] Stress test"
k6 run "$SCENARIOS_DIR/stress.js"

echo ""
echo ">>> [3/4] Spike test"
k6 run "$SCENARIOS_DIR/spike.js"

echo ""
echo ">>> [4/4] Soak test"
k6 run "$SCENARIOS_DIR/soak.js"

echo ""
echo "==============================="
echo " Todos os testes concluídos"
echo "==============================="