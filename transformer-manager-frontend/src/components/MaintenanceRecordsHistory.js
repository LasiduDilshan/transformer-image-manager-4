import React, { useState, useEffect } from "react";
import axios from "axios";
import {
  Card,
  Table,
  Badge,
  Button,
  Spinner,
  Alert,
  Modal,
  Row,
  Col,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faHistory,
  faEye,
  faExclamationTriangle,
  faCheckCircle,
  faDownload,
  faFileExcel,
  faFilePdf,
} from "@fortawesome/free-solid-svg-icons";
import { useAuth } from "../AuthContext";

const MaintenanceRecordsHistory = ({ transformerId }) => {
  const { token } = useAuth();
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedRecord, setSelectedRecord] = useState(null);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [pdfDownloadingId, setPdfDownloadingId] = useState(null);

  useEffect(() => {
    fetchRecords();
  }, [transformerId]);

  const fetchRecords = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `http://localhost:8080/api/maintenance-records/transformer/${transformerId}`,
        {
          headers: { Authorization: `Bearer ${token}` },
          withCredentials: true,
        }
      );
      setRecords(response.data);
      setError("");
    } catch (err) {
      setError("Failed to fetch maintenance records");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadge = (status) => {
    const statusMap = {
      OK: "success",
      NEEDS_MAINTENANCE: "warning",
      URGENT_ATTENTION: "danger",
    };
    return <Badge bg={statusMap[status] || "secondary"}>{status}</Badge>;
  };

  const getPriorityBadge = (priority) => {
    const priorityMap = {
      LOW: "info",
      MEDIUM: "warning",
      HIGH: "danger",
      CRITICAL: "danger",
    };
    return <Badge bg={priorityMap[priority] || "secondary"}>{priority}</Badge>;
  };

  const handleViewDetails = (record) => {
    setSelectedRecord(record);
    setShowDetailModal(true);
  };

  const convertToCSV = (data) => {
    if (!data || data.length === 0) return "";

    // Define headers
    const headers = [
      "Record ID",
      "Inspection Date",
      "Inspector Name",
      "Inspector ID",
      "Inspector Email",
      "Transformer Status",
      "Maintenance Priority",
      "Voltage Phase A (V)",
      "Voltage Phase B (V)",
      "Voltage Phase C (V)",
      "Current Phase A (A)",
      "Current Phase B (A)",
      "Current Phase C (A)",
      "Power Factor",
      "Frequency (Hz)",
      "Ambient Temperature (°C)",
      "Oil Temperature (°C)",
      "Winding Temperature (°C)",
      "Oil Level",
      "Oil Color",
      "Oil Analysis Remarks",
      "Cooling System Condition",
      "Bushing Condition",
      "Tank Condition",
      "Gauges Condition",
      "Weather Condition",
      "Load Condition",
      "Detected Anomalies",
      "Corrective Actions",
      "Recommended Action",
      "Scheduled Maintenance Date",
      "Parts Replaced",
      "Materials Used",
      "Requires Follow-up",
      "Follow-up Date",
      "Follow-up Notes",
      "Engineer Notes",
      "Additional Remarks",
      "Safety Observations",
      "Compliance Check",
      "Compliance Notes",
      "Record Status",
      "Created At",
      "Updated At",
      "Reviewed At",
    ];

    // Create CSV rows
    const rows = data.map((record) => [
      record.id || "",
      record.inspection?.inspectionDate
        ? new Date(record.inspection.inspectionDate).toLocaleString()
        : "",
      record.inspectorName || "",
      record.inspectorId || "",
      record.inspectorEmail || "",
      record.transformerStatus || "",
      record.maintenancePriority || "",
      record.voltagePhaseA || "",
      record.voltagePhaseB || "",
      record.voltagePhaseC || "",
      record.currentPhaseA || "",
      record.currentPhaseB || "",
      record.currentPhaseC || "",
      record.powerFactor || "",
      record.frequency || "",
      record.ambientTemperature || "",
      record.oilTemperature || "",
      record.windingTemperature || "",
      record.oilLevel || "",
      record.oilColor || "",
      record.oilAnalysisRemarks ? `"${record.oilAnalysisRemarks.replace(/"/g, '""')}"` : "",
      record.coolingSystemCondition ? `"${record.coolingSystemCondition.replace(/"/g, '""')}"` : "",
      record.bushingCondition ? `"${record.bushingCondition.replace(/"/g, '""')}"` : "",
      record.tankCondition ? `"${record.tankCondition.replace(/"/g, '""')}"` : "",
      record.gaugesCondition ? `"${record.gaugesCondition.replace(/"/g, '""')}"` : "",
      record.weatherCondition || "",
      record.loadCondition || "",
      record.detectedAnomalies ? `"${record.detectedAnomalies.replace(/"/g, '""')}"` : "",
      record.correctiveActions ? `"${record.correctiveActions.replace(/"/g, '""')}"` : "",
      record.recommendedAction ? `"${record.recommendedAction.replace(/"/g, '""')}"` : "",
      record.scheduledMaintenanceDate
        ? new Date(record.scheduledMaintenanceDate).toLocaleString()
        : "",
      record.partsReplaced ? `"${record.partsReplaced.replace(/"/g, '""')}"` : "",
      record.materialsUsed ? `"${record.materialsUsed.replace(/"/g, '""')}"` : "",
      record.requiresFollowUp ? "Yes" : "No",
      record.followUpDate
        ? new Date(record.followUpDate).toLocaleString()
        : "",
      record.followUpNotes ? `"${record.followUpNotes.replace(/"/g, '""')}"` : "",
      record.engineerNotes ? `"${record.engineerNotes.replace(/"/g, '""')}"` : "",
      record.additionalRemarks ? `"${record.additionalRemarks.replace(/"/g, '""')}"` : "",
      record.safetyObservations ? `"${record.safetyObservations.replace(/"/g, '""')}"` : "",
      record.complianceCheck ? "Yes" : "No",
      record.complianceNotes ? `"${record.complianceNotes.replace(/"/g, '""')}"` : "",
      record.recordStatus || "",
      record.createdAt ? new Date(record.createdAt).toLocaleString() : "",
      record.updatedAt ? new Date(record.updatedAt).toLocaleString() : "",
      record.reviewedAt ? new Date(record.reviewedAt).toLocaleString() : "",
    ]);

    // Combine headers and rows
    const csvContent = [
      headers.join(","),
      ...rows.map((row) => row.join(",")),
    ].join("\n");

    return csvContent;
  };

  const handleDownloadCSV = () => {
    try {
      setDownloading(true);
      
      // Convert records to CSV
      const csv = convertToCSV(records);
      
      // Create blob and download
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
      const link = document.createElement("a");
      const url = URL.createObjectURL(blob);
      
      // Generate filename with timestamp
      const timestamp = new Date().toISOString().split('T')[0];
      const filename = `maintenance_records_transformer_${transformerId}_${timestamp}.csv`;
      
      link.setAttribute("href", url);
      link.setAttribute("download", filename);
      link.style.visibility = "hidden";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      
      setDownloading(false);
    } catch (err) {
      console.error("Error downloading CSV:", err);
      setError("Failed to download CSV file");
      setDownloading(false);
    }
  };

  const handleDownloadSingleRecordCSV = (record) => {
    try {
      // Convert single record to CSV
      const csv = convertToCSV([record]);
      
      // Create blob and download
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
      const link = document.createElement("a");
      const url = URL.createObjectURL(blob);
      
      // Generate filename with record ID and date
      const date = record.inspection?.inspectionDate
        ? new Date(record.inspection.inspectionDate).toISOString().split('T')[0]
        : new Date().toISOString().split('T')[0];
      const filename = `maintenance_record_${record.id}_${date}.csv`;
      
      link.setAttribute("href", url);
      link.setAttribute("download", filename);
      link.style.visibility = "hidden";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      console.error("Error downloading CSV:", err);
      setError("Failed to download CSV file");
    }
  };

  const handleDownloadRecordPdf = async (recordId) => {
    if (!recordId) return;
    try {
      setPdfDownloadingId(recordId);
      const response = await axios.get(
        `http://localhost:8080/api/maintenance-records/${recordId}/export/pdf`,
        {
          headers: { Authorization: `Bearer ${token}` },
          withCredentials: true,
          responseType: "blob",
        }
      );

      const blob = new Blob([response.data], { type: "application/pdf" });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      const timestamp = new Date().toISOString().split("T")[0];
      link.href = url;
      link.download = `maintenance_record_${recordId}_${timestamp}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error("Error downloading PDF:", err);
      setError("Failed to download PDF file");
    } finally {
      setPdfDownloadingId(null);
    }
  };

  if (loading) {
    return (
      <div className="text-center p-5">
        <Spinner animation="border" variant="primary" />
        <p className="mt-2">Loading maintenance history...</p>
      </div>
    );
  }

  if (error) {
    return <Alert variant="danger">{error}</Alert>;
  }

  return (
    <>
      <Card className="mb-4">
        <Card.Header className="bg-info text-white d-flex justify-content-between align-items-center">
          <h5 className="mb-0">
            <FontAwesomeIcon icon={faHistory} className="me-2" />
            Maintenance Records History
          </h5>
          {records.length > 0 && (
            <Button
              variant="light"
              size="sm"
              onClick={handleDownloadCSV}
              disabled={downloading}
            >
              {downloading ? (
                <>
                  <Spinner
                    as="span"
                    animation="border"
                    size="sm"
                    className="me-2"
                  />
                  Downloading...
                </>
              ) : (
                <>
                  <FontAwesomeIcon icon={faFileExcel} className="me-2" />
                  Download All as CSV
                </>
              )}
            </Button>
          )}
        </Card.Header>
        <Card.Body>
          {records.length === 0 ? (
            <Alert variant="info">
              No maintenance records found for this transformer.
            </Alert>
          ) : (
            <Table striped bordered hover responsive>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Inspector</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Recommended Action</th>
                  <th>Record Status</th>
                  <th>Last Updated</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {records.map((record) => (
                  <tr key={record.id}>
                    <td>
                      {record.inspection?.inspectionDate
                        ? new Date(
                            record.inspection.inspectionDate
                          ).toLocaleDateString()
                        : "N/A"}
                    </td>
                    <td>{record.inspectorName || "N/A"}</td>
                    <td>{getStatusBadge(record.transformerStatus)}</td>
                    <td>{getPriorityBadge(record.maintenancePriority)}</td>
                    <td>
                      {record.recommendedAction
                        ? record.recommendedAction.substring(0, 50) + "..."
                        : "N/A"}
                    </td>
                    <td>
                      <Badge
                        bg={
                          record.recordStatus === "APPROVED"
                            ? "success"
                            : record.recordStatus === "SUBMITTED"
                            ? "primary"
                            : "secondary"
                        }
                      >
                        {record.recordStatus}
                      </Badge>
                    </td>
                    <td>
                      <small className="text-muted">
                        {record.updatedAt
                          ? new Date(record.updatedAt).toLocaleString()
                          : "N/A"}
                      </small>
                    </td>
                    <td>
                      <Button
                        variant="outline-primary"
                        size="sm"
                        className="me-2"
                        onClick={() => handleViewDetails(record)}
                      >
                        <FontAwesomeIcon icon={faEye} className="me-1" />
                        View
                      </Button>
                      <Button
                        variant="outline-success"
                        size="sm"
                        onClick={() => handleDownloadSingleRecordCSV(record)}
                      >
                        <FontAwesomeIcon icon={faDownload} className="me-1" />
                        CSV
                      </Button>
                      <Button
                        variant="outline-danger"
                        size="sm"
                        className="ms-2"
                        disabled={pdfDownloadingId === record.id}
                        onClick={() => handleDownloadRecordPdf(record.id)}
                      >
                        {pdfDownloadingId === record.id ? (
                          <>
                            <Spinner
                              as="span"
                              animation="border"
                              size="sm"
                              className="me-2"
                            />
                            Preparing...
                          </>
                        ) : (
                          <>
                            <FontAwesomeIcon icon={faFilePdf} className="me-1" />
                            PDF
                          </>
                        )}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card.Body>
      </Card>

      {/* Detail Modal */}
      <Modal
        show={showDetailModal}
        onHide={() => setShowDetailModal(false)}
        size="xl"
      >
        <Modal.Header closeButton>
          <Modal.Title>
            Maintenance Record Details
            {selectedRecord && (
              <Badge bg="secondary" className="ms-3">
                ID: {selectedRecord.id}
              </Badge>
            )}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {selectedRecord && (
            <>
              {/* Inspector Information */}
              <Card className="mb-3">
                <Card.Header>Inspector Information</Card.Header>
                <Card.Body>
                  <Row>
                    <Col md={4}>
                      <strong>Name:</strong> {selectedRecord.inspectorName || "N/A"}
                    </Col>
                    <Col md={4}>
                      <strong>ID:</strong> {selectedRecord.inspectorId || "N/A"}
                    </Col>
                    <Col md={4}>
                      <strong>Email:</strong> {selectedRecord.inspectorEmail || "N/A"}
                    </Col>
                  </Row>
                </Card.Body>
              </Card>

              {/* Status */}
              <Card className="mb-3">
                <Card.Header>Transformer Status</Card.Header>
                <Card.Body>
                  <Row>
                    <Col md={4}>
                      <strong>Status:</strong>{" "}
                      {getStatusBadge(selectedRecord.transformerStatus)}
                    </Col>
                    <Col md={4}>
                      <strong>Priority:</strong>{" "}
                      {getPriorityBadge(selectedRecord.maintenancePriority)}
                    </Col>
                    <Col md={4}>
                      <strong>Weather:</strong>{" "}
                      {selectedRecord.weatherCondition || "N/A"}
                    </Col>
                  </Row>
                </Card.Body>
              </Card>

              {/* Electrical Readings */}
              {(selectedRecord.voltagePhaseA ||
                selectedRecord.currentPhaseA ||
                selectedRecord.powerFactor) && (
                <Card className="mb-3">
                  <Card.Header>Electrical Readings</Card.Header>
                  <Card.Body>
                    <Row className="mb-2">
                      <Col md={12}>
                        <strong>Voltage (V):</strong> Phase A:{" "}
                        {selectedRecord.voltagePhaseA || "N/A"}, Phase B:{" "}
                        {selectedRecord.voltagePhaseB || "N/A"}, Phase C:{" "}
                        {selectedRecord.voltagePhaseC || "N/A"}
                      </Col>
                    </Row>
                    <Row className="mb-2">
                      <Col md={12}>
                        <strong>Current (A):</strong> Phase A:{" "}
                        {selectedRecord.currentPhaseA || "N/A"}, Phase B:{" "}
                        {selectedRecord.currentPhaseB || "N/A"}, Phase C:{" "}
                        {selectedRecord.currentPhaseC || "N/A"}
                      </Col>
                    </Row>
                    <Row>
                      <Col md={4}>
                        <strong>Power Factor:</strong>{" "}
                        {selectedRecord.powerFactor || "N/A"}
                      </Col>
                      <Col md={4}>
                        <strong>Frequency:</strong> {selectedRecord.frequency || "N/A"}{" "}
                        Hz
                      </Col>
                      <Col md={4}>
                        <strong>Load:</strong> {selectedRecord.loadCondition || "N/A"}
                      </Col>
                    </Row>
                  </Card.Body>
                </Card>
              )}

              {/* Temperature Readings */}
              {(selectedRecord.ambientTemperature ||
                selectedRecord.oilTemperature ||
                selectedRecord.windingTemperature) && (
                <Card className="mb-3">
                  <Card.Header>Temperature Readings</Card.Header>
                  <Card.Body>
                    <Row>
                      <Col md={4}>
                        <strong>Ambient:</strong>{" "}
                        {selectedRecord.ambientTemperature || "N/A"} °C
                      </Col>
                      <Col md={4}>
                        <strong>Oil:</strong> {selectedRecord.oilTemperature || "N/A"}{" "}
                        °C
                      </Col>
                      <Col md={4}>
                        <strong>Winding:</strong>{" "}
                        {selectedRecord.windingTemperature || "N/A"} °C
                      </Col>
                    </Row>
                  </Card.Body>
                </Card>
              )}

              {/* Oil Analysis */}
              {(selectedRecord.oilLevel ||
                selectedRecord.oilColor ||
                selectedRecord.oilAnalysisRemarks) && (
                <Card className="mb-3">
                  <Card.Header>Oil Analysis</Card.Header>
                  <Card.Body>
                    <Row className="mb-2">
                      <Col md={6}>
                        <strong>Oil Level:</strong> {selectedRecord.oilLevel || "N/A"}
                      </Col>
                      <Col md={6}>
                        <strong>Oil Color:</strong> {selectedRecord.oilColor || "N/A"}
                      </Col>
                    </Row>
                    {selectedRecord.oilAnalysisRemarks && (
                      <Row>
                        <Col md={12}>
                          <strong>Remarks:</strong> {selectedRecord.oilAnalysisRemarks}
                        </Col>
                      </Row>
                    )}
                  </Card.Body>
                </Card>
              )}

              {/* Visual Inspection
              {(selectedRecord.coolingSystemCondition ||
                selectedRecord.bushingCondition ||
                selectedRecord.tankCondition) && (
                <Card className="mb-3">
                  <Card.Header>Visual Inspection</Card.Header>
                  <Card.Body>
                    {selectedRecord.coolingSystemCondition && (
                      <p>
                        <strong>Cooling System:</strong>{" "}
                        {selectedRecord.coolingSystemCondition}
                      </p>
                    )}
                    {selectedRecord.bushingCondition && (
                      <p>
                        <strong>Bushing:</strong> {selectedRecord.bushingCondition}
                      </p>
                    )}
                    {selectedRecord.tankCondition && (
                      <p>
                        <strong>Tank:</strong> {selectedRecord.tankCondition}
                      </p>
                    )}
                    {selectedRecord.gaugesCondition && (
                      <p>
                        <strong>Gauges:</strong> {selectedRecord.gaugesCondition}
                      </p>
                    )}
                  </Card.Body>
                </Card>
              )} */}

              {/* Maintenance Actions */}
              <Card className="mb-3">
                <Card.Header>Maintenance Actions</Card.Header>
                <Card.Body>
                  {selectedRecord.detectedAnomalies && (
                    <p>
                      <strong>Detected Anomalies:</strong>
                      <br />
                      {selectedRecord.detectedAnomalies}
                    </p>
                  )}
                  {selectedRecord.correctiveActions && (
                    <p>
                      <strong>Corrective Actions:</strong>
                      <br />
                      {selectedRecord.correctiveActions}
                    </p>
                  )}
                  {selectedRecord.recommendedAction && (
                    <p>
                      <strong>Recommended Action:</strong>
                      <br />
                      {selectedRecord.recommendedAction}
                    </p>
                  )}
                  {selectedRecord.scheduledMaintenanceDate && (
                    <p>
                      <strong>Scheduled Maintenance:</strong>{" "}
                      {new Date(
                        selectedRecord.scheduledMaintenanceDate
                      ).toLocaleString()}
                    </p>
                  )}
                </Card.Body>
              </Card>

              {/* Parts and Materials */}
              {(selectedRecord.partsReplaced || selectedRecord.materialsUsed) && (
                <Card className="mb-3">
                  <Card.Header>Parts and Materials</Card.Header>
                  <Card.Body>
                    {selectedRecord.partsReplaced && (
                      <p>
                        <strong>Parts Replaced:</strong> {selectedRecord.partsReplaced}
                      </p>
                    )}
                    {selectedRecord.materialsUsed && (
                      <p>
                        <strong>Materials Used:</strong> {selectedRecord.materialsUsed}
                      </p>
                    )}
                  </Card.Body>
                </Card>
              )}

              {/* Follow-up */}
              {selectedRecord.requiresFollowUp && (
                <Card className="mb-3 border-warning">
                  <Card.Header className="bg-warning text-dark">
                    <FontAwesomeIcon
                      icon={faExclamationTriangle}
                      className="me-2"
                    />
                    Follow-up Required
                  </Card.Header>
                  <Card.Body>
                    {selectedRecord.followUpDate && (
                      <p>
                        <strong>Follow-up Date:</strong>{" "}
                        {new Date(selectedRecord.followUpDate).toLocaleString()}
                      </p>
                    )}
                    {selectedRecord.followUpNotes && (
                      <p>
                        <strong>Notes:</strong> {selectedRecord.followUpNotes}
                      </p>
                    )}
                  </Card.Body>
                </Card>
              )}

              {/* Engineer Notes */}
              {(selectedRecord.engineerNotes ||
                selectedRecord.additionalRemarks) && (
                <Card className="mb-3">
                  <Card.Header>Engineer Notes</Card.Header>
                  <Card.Body>
                    {selectedRecord.engineerNotes && (
                      <p>
                        <strong>Notes:</strong>
                        <br />
                        {selectedRecord.engineerNotes}
                      </p>
                    )}
                    {selectedRecord.additionalRemarks && (
                      <p>
                        <strong>Additional Remarks:</strong>
                        <br />
                        {selectedRecord.additionalRemarks}
                      </p>
                    )}
                  </Card.Body>
                </Card>
              )}

              {/* Safety and Compliance */}
              {(selectedRecord.safetyObservations ||
                selectedRecord.complianceCheck) && (
                <Card className="mb-3">
                  <Card.Header>Safety and Compliance</Card.Header>
                  <Card.Body>
                    {selectedRecord.complianceCheck && (
                      <p>
                        <FontAwesomeIcon
                          icon={faCheckCircle}
                          className="text-success me-2"
                        />
                        <strong>Compliance Check Completed</strong>
                      </p>
                    )}
                    {selectedRecord.safetyObservations && (
                      <p>
                        <strong>Safety Observations:</strong>
                        <br />
                        {selectedRecord.safetyObservations}
                      </p>
                    )}
                    {selectedRecord.complianceNotes && (
                      <p>
                        <strong>Compliance Notes:</strong>
                        <br />
                        {selectedRecord.complianceNotes}
                      </p>
                    )}
                  </Card.Body>
                </Card>
              )}

              {/* Record Metadata */}
              <Card className="mb-3 bg-light">
                <Card.Header>Record Information</Card.Header>
                <Card.Body>
                  <Row>
                    <Col md={6}>
                      <strong>Created:</strong>{" "}
                      {new Date(selectedRecord.createdAt).toLocaleString()}
                    </Col>
                    <Col md={6}>
                      <strong>Last Updated:</strong>{" "}
                      {new Date(selectedRecord.updatedAt).toLocaleString()}
                    </Col>
                  </Row>
                  <Row className="mt-2">
                    <Col md={6}>
                      <strong>Status:</strong>{" "}
                      <Badge
                        bg={
                          selectedRecord.recordStatus === "APPROVED"
                            ? "success"
                            : selectedRecord.recordStatus === "SUBMITTED"
                            ? "primary"
                            : "secondary"
                        }
                      >
                        {selectedRecord.recordStatus}
                      </Badge>
                    </Col>
                    {selectedRecord.reviewedAt && (
                      <Col md={6}>
                        <strong>Reviewed:</strong>{" "}
                        {new Date(selectedRecord.reviewedAt).toLocaleString()}
                      </Col>
                    )}
                  </Row>
                </Card.Body>
              </Card>
            </>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="success"
            onClick={() => handleDownloadSingleRecordCSV(selectedRecord)}
          >
            <FontAwesomeIcon icon={faDownload} className="me-2" />
            Download as CSV
          </Button>
          <Button
            variant="danger"
            disabled={!selectedRecord || pdfDownloadingId === selectedRecord?.id}
            onClick={() => handleDownloadRecordPdf(selectedRecord?.id)}
          >
            {pdfDownloadingId === selectedRecord?.id ? (
              <>
                <Spinner as="span" animation="border" size="sm" className="me-2" />
                Generating PDF...
              </>
            ) : (
              <>
                <FontAwesomeIcon icon={faFilePdf} className="me-2" />
                Download as PDF
              </>
            )}
          </Button>
          <Button variant="secondary" onClick={() => setShowDetailModal(false)}>
            Close
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
};

export default MaintenanceRecordsHistory;
