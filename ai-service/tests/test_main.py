import unittest
from datetime import datetime
from fastapi import HTTPException
from app.main import (
    health,
    predict_eta,
    forecast_demand,
    detect_anomalies,
    get_recommendations,
    verify_internal_secret,
    EtaPredictionRequest,
    DemandForecastRequest,
    HourlyVolumePoint,
    AnomalyDetectionRequest,
    AnomalyCheckMetric,
    OperationalRecommendationRequest,
    INTERNAL_API_SECRET
)

class TestAiService(unittest.TestCase):

    def test_health_check(self):
        res = health()
        self.assertEqual(res.status, "UP")
        self.assertEqual(res.application, "smartqueue-ai-service")
        self.assertIsInstance(res.checked_at, datetime)

    def test_verify_internal_secret(self):
        # Valid secret
        try:
            verify_internal_secret(INTERNAL_API_SECRET)
        except HTTPException:
            self.fail("Valid secret should not raise HTTPException")

        # Invalid secret
        with self.assertRaises(HTTPException) as ctx:
            verify_internal_secret("wrong-key")
        self.assertEqual(ctx.exception.status_code, 401)

        # Missing secret
        with self.assertRaises(HTTPException) as ctx:
            verify_internal_secret(None)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_predict_eta_zero_tokens(self):
        req = EtaPredictionRequest(
            queueId="00000000-0000-0000-0000-000000000001",
            tokensAhead=0,
            defaultServiceTimeMinutes=5
        )
        res = predict_eta(req)
        self.assertEqual(res.estimated_wait_minutes, 0)
        self.assertEqual(res.prediction_type, "AI_REGRESSION")
        self.assertIn("factors", res.__dict__)

    def test_predict_eta_positive_tokens(self):
        req = EtaPredictionRequest(
            queueId="00000000-0000-0000-0000-000000000001",
            tokensAhead=4,
            defaultServiceTimeMinutes=5,
            activeCountersCount=2,
            rollingAvgServiceMinutes=4.5
        )
        res = predict_eta(req)
        self.assertGreater(res.estimated_wait_minutes, 0)
        self.assertIn("factors", res.__dict__)

    def test_forecast_demand_sufficient_data(self):
        volumes = [
            HourlyVolumePoint(date="2026-09-23", hour=h % 24, tokenCount=10 + h) for h in range(12)
        ]
        req = DemandForecastRequest(
            queueId="00000000-0000-0000-0000-000000000001",
            horizonHours=4,
            historicalHourlyVolumes=volumes
        )
        res = forecast_demand(req)
        self.assertEqual(res.status, "SUCCESS")
        self.assertEqual(len(res.forecast), 4)

    def test_forecast_demand_insufficient_data(self):
        volumes = [
            HourlyVolumePoint(date="2026-09-23", hour=1, tokenCount=5),
            HourlyVolumePoint(date="2026-09-23", hour=2, tokenCount=6)
        ]
        req = DemandForecastRequest(
            queueId="00000000-0000-0000-0000-000000000001",
            horizonHours=4,
            historicalHourlyVolumes=volumes
        )
        res = forecast_demand(req)
        self.assertEqual(res.status, "INSUFFICIENT_DATA")

    def test_detect_anomalies(self):
        metrics = [
            AnomalyCheckMetric(
                metricName="WAIT_TIME",
                observedValue=45.0,
                baselineMean=10.0,
                baselineStdDev=5.0,
                timestamp="2026-09-23T12:00:00Z"
            )
        ]
        req = AnomalyDetectionRequest(
            queueId="00000000-0000-0000-0000-000000000001",
            metrics=metrics
        )
        res = detect_anomalies(req)
        self.assertIn("anomalies", res.__dict__)
        self.assertGreater(len(res.anomalies), 0)
        self.assertEqual(res.anomalies[0].severity, "CRITICAL")

    def test_get_recommendations(self):
        req = OperationalRecommendationRequest(
            queueId="00000000-0000-0000-0000-000000000001",
            queueName="Test Queue",
            waitingCount=35,
            averageWaitMinutes=30.0,
            activeCounters=1,
            cancellationRate=25.0
        )
        res = get_recommendations(req)
        self.assertIn("recommendations", res.__dict__)
        self.assertGreater(len(res.recommendations), 0)

    def test_verify_document_valid_id(self):
        from app.main import DocumentVerificationRequest, verify_document
        req = DocumentVerificationRequest(
            documentType="GOVERNMENT_ID",
            fileName="national_id_card.pdf",
            fileContentBase64="JVBERi0xLjQKJcTl8uXr...",
            expectedHolderName="John Doe"
        )
        res = verify_document(req)
        self.assertEqual(res.status, "VERIFIED")
        self.assertGreaterEqual(res.confidence, 0.90)
        self.assertEqual(res.extracted_data.document_type, "GOVERNMENT_ID")
        self.assertIsNotNone(res.extracted_data.document_number)
        self.assertIn("John Doe", res.extracted_data.holder_name)

    def test_verify_document_invalid_extension(self):
        from app.main import DocumentVerificationRequest, verify_document
        req = DocumentVerificationRequest(
            documentType="GOVERNMENT_ID",
            fileName="malicious_payload.exe",
            expectedHolderName="John Doe"
        )
        res = verify_document(req)
        self.assertEqual(res.status, "REJECTED")
        self.assertIn("Unsupported", res.summary)

    def test_verify_document_expired(self):
        from app.main import DocumentVerificationRequest, verify_document
        req = DocumentVerificationRequest(
            documentType="GOVERNMENT_ID",
            fileName="expired_passport_2020.jpg",
            expectedHolderName="John Doe"
        )
        res = verify_document(req)
        self.assertEqual(res.status, "REJECTED")
        self.assertIn("expired", res.summary.lower())

    def test_verify_document_manual_review_needed(self):
        from app.main import DocumentVerificationRequest, verify_document
        req = DocumentVerificationRequest(
            documentType="MEDICAL_PRESCRIPTION",
            fileName="blurry_prescription_scan.png",
            expectedHolderName="Alice Smith"
        )
        res = verify_document(req)
        self.assertEqual(res.status, "MANUAL_REVIEW_REQUIRED")
        self.assertLess(res.confidence, 0.75)

if __name__ == "__main__":
    unittest.main()
