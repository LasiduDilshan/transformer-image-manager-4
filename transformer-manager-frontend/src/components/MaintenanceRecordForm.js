import React, { useState, useEffect } from "react";
import axios from "axios";
import {
  Card,
  Form,
  Button,
  Row,
  Col,
  Alert,
  Spinner,
  Tabs,
  Tab,
  Badge,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faSave,
  faCheckCircle,
  faExclamationTriangle,
  faInfoCircle,
} from "@fortawesome/free-solid-svg-icons";
import { useAuth } from "../AuthContext";

const MaintenanceRecordForm = ({ inspectionId, inspection }) => {
  const { token } = useAuth();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [activeTab, setActiveTab] = useState("inspector");
  const [recordMetadata, setRecordMetadata] = useState({
    createdAt: null,
    updatedAt: null,
    id: null,
  });

  // Form state
  const [formData, setFormData] = useState({
    // Inspector Information
    inspectorName: "",
    inspectorId: "",
    inspectorEmail: "",

    // Transformer Status
    transformerStatus: "",

    // Electrical Readings
    voltagePhaseA: "",
    voltagePhaseB: "",
    voltagePhaseC: "",
    currentPhaseA: "",
    currentPhaseB: "",
    currentPhaseC: "",
    powerFactor: "",
    frequency: "",
    ambientTemperature: "",
    oilTemperature: "",
    windingTemperature: "",

    // Oil Analysis
    oilLevel: "",
    oilColor: "",
    oilAnalysisRemarks: "",

    // Visual Inspection
    coolingSystemCondition: "",
    bushingCondition: "",
    tankCondition: "",
    gaugesCondition: "",

    // Maintenance Actions
    detectedAnomalies: "",
    correctiveActions: "",
    recommendedAction: "",
    maintenancePriority: "",
    scheduledMaintenanceDate: "",

    // Additional Information
    engineerNotes: "",
    additionalRemarks: "",
    weatherCondition: "",
    loadCondition: "",

    // Parts and Materials
    partsReplaced: "",
    materialsUsed: "",

    // Follow-up Information
    requiresFollowUp: false,
    followUpDate: "",
    followUpNotes: "",

    // Safety and Compliance
    safetyObservations: "",
    complianceCheck: false,
    complianceNotes: "",

    recordStatus: "DRAFT",
  });

  useEffect(() => {
    fetchMaintenanceRecord();
  }, [inspectionId]);

  const fetchMaintenanceRecord = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `http://localhost:8080/api/maintenance-records/inspection/${inspectionId}`,
        {
          headers: { Authorization: `Bearer ${token}` },
          withCredentials: true,
        }
      );

      if (response.data) {
        // Map the response data to form fields
        const record = response.data;
        
        // Store metadata
        setRecordMetadata({
          createdAt: record.createdAt,
          updatedAt: record.updatedAt,
          id: record.id,
        });
        
        setFormData({
          inspectorName: record.inspectorName || "",
          inspectorId: record.inspectorId || "",
          inspectorEmail: record.inspectorEmail || "",
          transformerStatus: record.transformerStatus || "",
          voltagePhaseA: record.voltagePhaseA || "",
          voltagePhaseB: record.voltagePhaseB || "",
          voltagePhaseC: record.voltagePhaseC || "",
          currentPhaseA: record.currentPhaseA || "",
          currentPhaseB: record.currentPhaseB || "",
          currentPhaseC: record.currentPhaseC || "",
          powerFactor: record.powerFactor || "",
          frequency: record.frequency || "",
          ambientTemperature: record.ambientTemperature || "",
          oilTemperature: record.oilTemperature || "",
          windingTemperature: record.windingTemperature || "",
          oilLevel: record.oilLevel || "",
          oilColor: record.oilColor || "",
          oilAnalysisRemarks: record.oilAnalysisRemarks || "",
          coolingSystemCondition: record.coolingSystemCondition || "",
          bushingCondition: record.bushingCondition || "",
          tankCondition: record.tankCondition || "",
          gaugesCondition: record.gaugesCondition || "",
          detectedAnomalies: record.detectedAnomalies || "",
          correctiveActions: record.correctiveActions || "",
          recommendedAction: record.recommendedAction || "",
          maintenancePriority: record.maintenancePriority || "",
          scheduledMaintenanceDate: record.scheduledMaintenanceDate
            ? record.scheduledMaintenanceDate.slice(0, 16)
            : "",
          engineerNotes: record.engineerNotes || "",
          additionalRemarks: record.additionalRemarks || "",
          weatherCondition: record.weatherCondition || "",
          loadCondition: record.loadCondition || "",
          partsReplaced: record.partsReplaced || "",
          materialsUsed: record.materialsUsed || "",
          requiresFollowUp: record.requiresFollowUp || false,
          followUpDate: record.followUpDate
            ? record.followUpDate.slice(0, 16)
            : "",
          followUpNotes: record.followUpNotes || "",
          safetyObservations: record.safetyObservations || "",
          complianceCheck: record.complianceCheck || false,
          complianceNotes: record.complianceNotes || "",
          recordStatus: record.recordStatus || "DRAFT",
        });
      }
    } catch (err) {
      if (err.response?.status === 404) {
        // No record exists yet, use default values
        console.log("No maintenance record found, using defaults");
      } else {
        console.error("Error fetching maintenance record:", err);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData({
      ...formData,
      [name]: type === "checkbox" ? checked : value,
    });
  };

  const handleSave = async (submit = false) => {
    try {
      setSaving(true);
      setError("");
      setSuccess("");

      const dataToSend = {
        ...formData,
        recordStatus: submit ? "SUBMITTED" : "DRAFT",
      };

      await axios.post(
        `http://localhost:8080/api/maintenance-records/inspection/${inspectionId}`,
        dataToSend,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
          },
          withCredentials: true,
        }
      );

      setSuccess(
        submit
          ? "Maintenance record submitted successfully!"
          : "Maintenance record saved as draft!"
      );
      setTimeout(() => setSuccess(""), 3000);
    } catch (err) {
      setError(
        err.response?.data?.message || "Failed to save maintenance record"
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="text-center p-5">
        <Spinner animation="border" variant="primary" />
        <p className="mt-2">Loading maintenance record...</p>
      </div>
    );
  }

  return (
    <Card className="mb-4">
      <Card.Header className="bg-primary text-white">
        <h4 className="mb-0">
          <FontAwesomeIcon icon={faInfoCircle} className="me-2" />
          Maintenance Record Form
        </h4>
      </Card.Header>
      <Card.Body>
        {error && (
          <Alert variant="danger" dismissible onClose={() => setError("")}>
            {error}
          </Alert>
        )}
        {success && (
          <Alert variant="success" dismissible onClose={() => setSuccess("")}>
            {success}
          </Alert>
        )}

        {/* Last Updated Badge */}
        {recordMetadata.updatedAt && (
          <Alert variant="info" className="d-flex justify-content-between align-items-center">
            <div>
              <strong>Maintenance Record Status:</strong>{" "}
              <Badge bg={formData.recordStatus === "SUBMITTED" ? "primary" : formData.recordStatus === "APPROVED" ? "success" : "secondary"}>
                {formData.recordStatus}
              </Badge>
            </div>
            <div className="text-muted">
              <small>
                <strong>Last Updated:</strong>{" "}
                {new Date(recordMetadata.updatedAt).toLocaleString()}
              </small>
            </div>
          </Alert>
        )}

        {/* Transformer Metadata (Read-only) */}
        <Card className="mb-3 bg-light">
          <Card.Body>
            <h5 className="mb-3">Transformer Metadata</h5>
            <Row>
              <Col md={3}>
                <strong>ID:</strong> {inspection?.transformerRecord?.id || "N/A"}
              </Col>
              <Col md={3}>
                <strong>Name:</strong> {inspection?.transformerRecord?.name || "N/A"}
              </Col>
              <Col md={3}>
                <strong>Location:</strong>{" "}
                {inspection?.transformerRecord?.locationName || "N/A"}
              </Col>
              <Col md={3}>
                <strong>Capacity:</strong>{" "}
                {inspection?.transformerRecord?.capacity
                  ? `${inspection.transformerRecord.capacity} kVA`
                  : "N/A"}
              </Col>
            </Row>
            <Row className="mt-2">
              <Col md={6}>
                <strong>Inspection Date:</strong>{" "}
                {inspection?.inspectionDate
                  ? new Date(inspection.inspectionDate).toLocaleString()
                  : "N/A"}
              </Col>
              <Col md={6}>
                <strong>Conducted By:</strong>{" "}
                {inspection?.conductedBy?.displayName ||
                  inspection?.conductedByUser?.displayName ||
                  inspection?.conductedByAdmin?.displayName ||
                  "N/A"}
              </Col>
            </Row>
          </Card.Body>
        </Card>

        <Tabs
          activeKey={activeTab}
          onSelect={(k) => setActiveTab(k)}
          className="mb-3"
        >
          {/* Tab 1: Inspector Information */}
          <Tab
            eventKey="inspector"
            title={
              <>
                <FontAwesomeIcon icon={faInfoCircle} className="me-2" />
                Inspector Info
              </>
            }
          >
            <Row>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Inspector Name *</Form.Label>
                  <Form.Control
                    type="text"
                    name="inspectorName"
                    value={formData.inspectorName}
                    onChange={handleChange}
                    placeholder="Enter inspector name"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Inspector ID</Form.Label>
                  <Form.Control
                    type="text"
                    name="inspectorId"
                    value={formData.inspectorId}
                    onChange={handleChange}
                    placeholder="Enter inspector ID"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Inspector Email</Form.Label>
                  <Form.Control
                    type="email"
                    name="inspectorEmail"
                    value={formData.inspectorEmail}
                    onChange={handleChange}
                    placeholder="Enter email"
                  />
                </Form.Group>
              </Col>
            </Row>

            <Form.Group className="mb-3">
              <Form.Label>Transformer Status *</Form.Label>
              <Form.Select
                name="transformerStatus"
                value={formData.transformerStatus}
                onChange={handleChange}
              >
                <option value="">Select Status</option>
                <option value="OK">OK - Normal Operation</option>
                <option value="NEEDS_MAINTENANCE">Needs Maintenance</option>
                <option value="URGENT_ATTENTION">Urgent Attention Required</option>
              </Form.Select>
            </Form.Group>
          </Tab>

          {/* Tab 2: Electrical Readings */}
          <Tab
            eventKey="electrical"
            title={
              <>
                <FontAwesomeIcon icon={faExclamationTriangle} className="me-2" />
                Electrical Readings
              </>
            }
          >
            <h6 className="mb-3">Voltage Readings (V)</h6>
            <Row>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Phase A</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.01"
                    name="voltagePhaseA"
                    value={formData.voltagePhaseA}
                    onChange={handleChange}
                    placeholder="Voltage (V)"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Phase B</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.01"
                    name="voltagePhaseB"
                    value={formData.voltagePhaseB}
                    onChange={handleChange}
                    placeholder="Voltage (V)"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Phase C</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.01"
                    name="voltagePhaseC"
                    value={formData.voltagePhaseC}
                    onChange={handleChange}
                    placeholder="Voltage (V)"
                  />
                </Form.Group>
              </Col>
            </Row>

            <h6 className="mb-3">Current Readings (A)</h6>
            <Row>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Phase A</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.01"
                    name="currentPhaseA"
                    value={formData.currentPhaseA}
                    onChange={handleChange}
                    placeholder="Current (A)"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Phase B</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.01"
                    name="currentPhaseB"
                    value={formData.currentPhaseB}
                    onChange={handleChange}
                    placeholder="Current (A)"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Phase C</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.01"
                    name="currentPhaseC"
                    value={formData.currentPhaseC}
                    onChange={handleChange}
                    placeholder="Current (A)"
                  />
                </Form.Group>
              </Col>
            </Row>

            <h6 className="mb-3">Other Parameters</h6>
            <Row>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Power Factor</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.01"
                    name="powerFactor"
                    value={formData.powerFactor}
                    onChange={handleChange}
                    placeholder="0.0 - 1.0"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Frequency (Hz)</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.01"
                    name="frequency"
                    value={formData.frequency}
                    onChange={handleChange}
                    placeholder="50 or 60 Hz"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Load Condition</Form.Label>
                  <Form.Select
                    name="loadCondition"
                    value={formData.loadCondition}
                    onChange={handleChange}
                  >
                    <option value="">Select Load</option>
                    <option value="NO_LOAD">No Load</option>
                    <option value="LIGHT_LOAD">Light Load</option>
                    <option value="NORMAL_LOAD">Normal Load</option>
                    <option value="HEAVY_LOAD">Heavy Load</option>
                    <option value="OVERLOAD">Overload</option>
                  </Form.Select>
                </Form.Group>
              </Col>
            </Row>

            <h6 className="mb-3">Temperature Readings (°C)</h6>
            <Row>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Ambient Temperature</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.1"
                    name="ambientTemperature"
                    value={formData.ambientTemperature}
                    onChange={handleChange}
                    placeholder="°C"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Oil Temperature</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.1"
                    name="oilTemperature"
                    value={formData.oilTemperature}
                    onChange={handleChange}
                    placeholder="°C"
                  />
                </Form.Group>
              </Col>
              <Col md={4}>
                <Form.Group className="mb-3">
                  <Form.Label>Winding Temperature</Form.Label>
                  <Form.Control
                    type="number"
                    step="0.1"
                    name="windingTemperature"
                    value={formData.windingTemperature}
                    onChange={handleChange}
                    placeholder="°C"
                  />
                </Form.Group>
              </Col>
            </Row>
          </Tab>

          {/* Tab 3: Visual Inspection
          <Tab
            eventKey="visual"
            title={
              <>
                <FontAwesomeIcon icon={faCheckCircle} className="me-2" />
                Visual Inspection
              </>
            }
          >
            <Row>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Oil Level</Form.Label>
                  <Form.Select
                    name="oilLevel"
                    value={formData.oilLevel}
                    onChange={handleChange}
                  >
                    <option value="">Select Oil Level</option>
                    <option value="NORMAL">Normal</option>
                    <option value="LOW">Low</option>
                    <option value="CRITICAL">Critical</option>
                  </Form.Select>
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Oil Color</Form.Label>
                  <Form.Select
                    name="oilColor"
                    value={formData.oilColor}
                    onChange={handleChange}
                  >
                    <option value="">Select Oil Color</option>
                    <option value="CLEAR">Clear</option>
                    <option value="LIGHT_BROWN">Light Brown</option>
                    <option value="DARK_BROWN">Dark Brown</option>
                    <option value="BLACK">Black</option>
                  </Form.Select>
                </Form.Group>
              </Col>
            </Row>

            <Form.Group className="mb-3">
              <Form.Label>Oil Analysis Remarks</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                name="oilAnalysisRemarks"
                value={formData.oilAnalysisRemarks}
                onChange={handleChange}
                placeholder="Enter oil analysis observations..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Cooling System Condition</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                name="coolingSystemCondition"
                value={formData.coolingSystemCondition}
                onChange={handleChange}
                placeholder="Describe cooling system condition..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Bushing Condition</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                name="bushingCondition"
                value={formData.bushingCondition}
                onChange={handleChange}
                placeholder="Describe bushing condition..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Tank Condition</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                name="tankCondition"
                value={formData.tankCondition}
                onChange={handleChange}
                placeholder="Describe tank condition..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Gauges and Indicators Condition</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                name="gaugesCondition"
                value={formData.gaugesCondition}
                onChange={handleChange}
                placeholder="Describe gauges condition..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Weather Condition</Form.Label>
              <Form.Select
                name="weatherCondition"
                value={formData.weatherCondition}
                onChange={handleChange}
              >
                <option value="">Select Weather</option>
                <option value="CLEAR">Clear</option>
                <option value="CLOUDY">Cloudy</option>
                <option value="RAINY">Rainy</option>
                <option value="STORMY">Stormy</option>
              </Form.Select>
            </Form.Group>
          </Tab> */}

          {/* Tab 4: Maintenance Actions */}
          <Tab
            eventKey="maintenance"
            title={
              <>
                <FontAwesomeIcon icon={faExclamationTriangle} className="me-2" />
                Maintenance Actions
              </>
            }
          >
            <Form.Group className="mb-3">
              <Form.Label>Detected Anomalies</Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                name="detectedAnomalies"
                value={formData.detectedAnomalies}
                onChange={handleChange}
                placeholder="List all detected anomalies from thermal analysis and visual inspection..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Corrective Actions Taken</Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                name="correctiveActions"
                value={formData.correctiveActions}
                onChange={handleChange}
                placeholder="Describe corrective actions performed..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Recommended Action *</Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                name="recommendedAction"
                value={formData.recommendedAction}
                onChange={handleChange}
                placeholder="Recommend future actions or maintenance..."
              />
            </Form.Group>

            <Row>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Maintenance Priority</Form.Label>
                  <Form.Select
                    name="maintenancePriority"
                    value={formData.maintenancePriority}
                    onChange={handleChange}
                  >
                    <option value="">Select Priority</option>
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="CRITICAL">Critical</option>
                  </Form.Select>
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group className="mb-3">
                  <Form.Label>Scheduled Maintenance Date</Form.Label>
                  <Form.Control
                    type="datetime-local"
                    name="scheduledMaintenanceDate"
                    value={formData.scheduledMaintenanceDate}
                    onChange={handleChange}
                  />
                </Form.Group>
              </Col>
            </Row>

            <Form.Group className="mb-3">
              <Form.Label>Parts Replaced</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                name="partsReplaced"
                value={formData.partsReplaced}
                onChange={handleChange}
                placeholder="List parts replaced during maintenance..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Materials Used</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                name="materialsUsed"
                value={formData.materialsUsed}
                onChange={handleChange}
                placeholder="List materials used..."
              />
            </Form.Group>
          </Tab>

          {/* Tab 5: Follow-up & Notes */}
          <Tab
            eventKey="followup"
            title={
              <>
                <FontAwesomeIcon icon={faInfoCircle} className="me-2" />
                Follow-up & Notes
              </>
            }
          >
            <Form.Group className="mb-3">
              <Form.Check
                type="checkbox"
                name="requiresFollowUp"
                checked={formData.requiresFollowUp}
                onChange={handleChange}
                label="Requires Follow-up Inspection"
              />
            </Form.Group>

            {formData.requiresFollowUp && (
              <>
                <Form.Group className="mb-3">
                  <Form.Label>Follow-up Date</Form.Label>
                  <Form.Control
                    type="datetime-local"
                    name="followUpDate"
                    value={formData.followUpDate}
                    onChange={handleChange}
                  />
                </Form.Group>

                <Form.Group className="mb-3">
                  <Form.Label>Follow-up Notes</Form.Label>
                  <Form.Control
                    as="textarea"
                    rows={3}
                    name="followUpNotes"
                    value={formData.followUpNotes}
                    onChange={handleChange}
                    placeholder="Describe what should be checked during follow-up..."
                  />
                </Form.Group>
              </>
            )}

            <Form.Group className="mb-3">
              <Form.Label>Engineer Notes</Form.Label>
              <Form.Control
                as="textarea"
                rows={4}
                name="engineerNotes"
                value={formData.engineerNotes}
                onChange={handleChange}
                placeholder="Additional engineer notes and observations..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Additional Remarks</Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                name="additionalRemarks"
                value={formData.additionalRemarks}
                onChange={handleChange}
                placeholder="Any additional remarks..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Safety Observations</Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                name="safetyObservations"
                value={formData.safetyObservations}
                onChange={handleChange}
                placeholder="Document any safety concerns or observations..."
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Check
                type="checkbox"
                name="complianceCheck"
                checked={formData.complianceCheck}
                onChange={handleChange}
                label="Compliance Check Completed"
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label>Compliance Notes</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                name="complianceNotes"
                value={formData.complianceNotes}
                onChange={handleChange}
                placeholder="Document compliance with standards and regulations..."
              />
            </Form.Group>
          </Tab>
        </Tabs>

        {/* Action Buttons */}
        <div className="d-flex justify-content-between mt-4">
          <div>
            <Badge bg="info" className="fs-6">
              Status: {formData.recordStatus}
            </Badge>
          </div>
          <div>
            <Button
              variant="outline-primary"
              className="me-2"
              onClick={() => handleSave(false)}
              disabled={saving}
            >
              {saving ? (
                <>
                  <Spinner
                    as="span"
                    animation="border"
                    size="sm"
                    className="me-2"
                  />
                  Saving...
                </>
              ) : (
                <>
                  <FontAwesomeIcon icon={faSave} className="me-2" />
                  Save as Draft
                </>
              )}
            </Button>
            <Button
              variant="success"
              onClick={() => handleSave(true)}
              disabled={saving}
            >
              {saving ? (
                <>
                  <Spinner
                    as="span"
                    animation="border"
                    size="sm"
                    className="me-2"
                  />
                  Submitting...
                </>
              ) : (
                <>
                  <FontAwesomeIcon icon={faCheckCircle} className="me-2" />
                  Submit Record
                </>
              )}
            </Button>
          </div>
        </div>
      </Card.Body>
    </Card>
  );
};

export default MaintenanceRecordForm;
