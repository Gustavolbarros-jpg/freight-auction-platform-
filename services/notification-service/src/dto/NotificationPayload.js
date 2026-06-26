/**
 * Monta o payload padronizado que sera enviado via WebSocket para os clientes
 * @param {string} type
 * @param {string} auctionId
 * @param {object} data
 * @returns {string} JSON serializado
 */
function buildPayload(type, auctionId, data) {
    return JSON.stringify({
        type,
        auctionId,
        data,
        sentAt: new Date().toISOString()
    });
}

module.exports = {buildPayload};