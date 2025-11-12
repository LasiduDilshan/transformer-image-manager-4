import React, { useState, useEffect } from "react";
import axios from "axios";
import {
  Modal,
  Button,
  Card,
  Row,
  Col,
  Spinner,
  Alert,
  Badge,
  Image,
  Form,
  Tabs,
  Tab,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faArrowLeft,
  faMapMarkerAlt,
  faUser,
  faCalendar,
  faTrashCan,
  faClock,
  faPlus,
  faTimes,
  faUpload,
  faFileAlt,
  faHistory,
  faImages,
  faExclamationTriangle,
} from "@fortawesome/free-solid-svg-icons";
import ImageViewer from "./ImageViewer";
import AnalysisDisplay from "./AnalysisDisplay";
import MaintenanceRecordForm from "./MaintenanceRecordForm";
import MaintenanceRecordsHistory from "./MaintenanceRecordsHistory";
import { useParams, useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";

const InspectionDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { token, isAuthenticated, user: authUser } = useAuth();
  const [inspection, setInspection] = useState(null);
  const [transformerRecord, setTransformerRecord] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState("images");

  // Image upload states
  const [showImageUpload, setShowImageUpload] = useState(false);
  const [newImages, setNewImages] = useState([]);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const [uploadSuccess, setUploadSuccess] = useState("");

  // Image viewer states
  const [showImageViewer, setShowImageViewer] = useState(false);
  const [viewerImages, setViewerImages] = useState([]);
  const [viewerCurrentIndex, setViewerCurrentIndex] = useState(0);
  const [viewerTitle, setViewerTitle] = useState("");

  // Delete states
  const [deletingImageId, setDeletingImageId] = useState(null);
  const [deleteError, setDeleteError] = useState("");

  useEffect(() => {
    const fetchInspection = async () => {
      try {
        setLoading(true);

        // Check if user is authenticated
        if (!isAuthenticated || !token) {
          setError(
            "Authentication required. Please log in to view inspection details."
          );
          return;
        }

        const config = {
          headers: { Authorization: `Bearer ${token}` },
          withCredentials: true,
        };

        const response = await axios.get(
          `http://localhost:8080/api/inspections/${id}`,
          config
        );
        setInspection(response.data);

        // Fetch transformer record separately to get all images (same as TransformerRecordDetail)
        if (response.data.transformerRecord?.id) {
          const transformerResponse = await axios.get(
            `http://localhost:8080/api/transformer-records/${response.data.transformerRecord.id}`,
            config
          );
          setTransformerRecord(transformerResponse.data);
        }

        setError("");
      } catch (err) {
        if (err.response?.status === 403) {
          setError(
            "Access denied. You don't have permission to view this inspection."
          );
        } else if (err.response?.status === 401) {
          setError("Authentication expired. Please log in again.");
        } else {
          setError("Failed to fetch inspection details");
        }
      } finally {
        setLoading(false);
      }
    };

    fetchInspection();
  }, [id, token, isAuthenticated]);

  // Debug: Log auth and inspection data
  useEffect(() => {
    // console.log("=== DEBUG INFORMATION ===");
    // console.log("Auth User:", authUser);
    // console.log("Inspection:", inspection);
    // console.log("Can delete images:", canDeleteImages());
    // console.log("Is authenticated:", isAuthenticated);
    // console.log("Maintenance images count:", inspection?.images?.length || 0);
    // console.log("=== END DEBUG ===");
  }, [authUser, inspection, isAuthenticated]);

  // Get ALL images from transformer record (same as TransformerRecordDetail)
  const allImages = transformerRecord?.images || [];

  // Get maintenance images from this inspection
  const maintenanceImages = inspection?.images || [];

  // Check if current user can delete images from this inspection
  const canDeleteImages = () => {
    // console.log("=== Checking delete permissions ===");
    // console.log("Inspection:", inspection);
    // console.log("Auth User:", authUser);

    if (!inspection) {
      // console.log("Cannot delete: No inspection data");
      return false;
    }

    if (!authUser) {
      // console.log("Cannot delete: No auth user data");
      return false;
    }

    if (!isAuthenticated) {
      // console.log("Cannot delete: Not authenticated");
      return false;
    }

    // If user is ADMIN, always allow delete
    const isAdmin = authUser.role === "ADMIN" || authUser.role === "ROLE_ADMIN";
    if (isAdmin) {
      // console.log("User is ADMIN - can delete");
      return true;
    }

    // Users can only delete from their own inspections
    const userConductedInspection =
      inspection.conductedByUser &&
      inspection.conductedByUser.id === authUser.id;

    if (userConductedInspection) {
      // console.log("User conducted this inspection - can delete");
      return true;
    }

    // Additional check for conductedBy field (backward compatibility)
    if (inspection.conductedBy && inspection.conductedBy.id === authUser.id) {
      // console.log("User conducted this inspection (legacy field) - can delete");
      return true;
    }

    // console.log("Cannot delete - no permission match");
    // console.log("Conducted by user:", inspection.conductedByUser);
    // console.log("Auth user ID:", authUser.id);
    // console.log("=== End permission check ===");

    return false;
  };

  // SIMPLE VERSION FOR TESTING - REMOVE THIS IN PRODUCTION
  const canDeleteImagesSimple = () => {
    // For testing, allow all authenticated users to delete
    // console.log("TEST MODE: Allowing delete for all authenticated users");
    return isAuthenticated;
  };

  // Image viewer handlers
  const openImageViewer = (images, index = 0, title = "Images") => {
    setViewerImages(images);
    setViewerCurrentIndex(index);
    setViewerTitle(title);
    setShowImageViewer(true);
  };

  // Image upload handlers
  const addImageField = () => {
    setNewImages([...newImages, null]);
  };

  const removeImageField = (index) => {
    const updatedImages = [...newImages];
    updatedImages.splice(index, 1);
    setNewImages(updatedImages);
  };

  const handleImageChange = (index, file) => {
    const updatedImages = [...newImages];
    updatedImages[index] = file;
    setNewImages(updatedImages);
  };

  const handleImageUpload = async () => {
    if (!isAuthenticated || !token) {
      setUploadError("Authentication required. Please log in again.");
      return;
    }

    const validImages = newImages.filter((img) => img !== null);
    if (validImages.length === 0) {
      setUploadError("Please select at least one image to upload.");
      return;
    }

    setUploadLoading(true);
    setUploadError("");
    setUploadSuccess("");

    try {
      const formData = new FormData();
      validImages.forEach((image) => {
        formData.append("images", image);
      });

      const config = {
        headers: {
          "Content-Type": "multipart/form-data",
          Authorization: `Bearer ${token}`,
        },
        withCredentials: true,
      };

      await axios.post(
        `http://localhost:8080/api/inspections/${id}/images`,
        formData,
        config
      );

      setUploadError("");
      setUploadSuccess(`${validImages.length} image(s) uploaded successfully!`);

      // Refresh inspection data to show new images; falling back to manual reload if it fails.
      try {
        const response = await axios.get(
          `http://localhost:8080/api/inspections/${id}`,
          {
            headers: { Authorization: `Bearer ${token}` },
            withCredentials: true,
          }
        );
        setInspection(response.data);
      } catch (refreshError) {
        console.error("Inspection refresh failed after upload", refreshError);
        setUploadError(
          "Images uploaded, but failed to refresh inspection details automatically. Please refresh the page."
        );
      }

      // Reset form
      setNewImages([]);
      setTimeout(() => {
        setShowImageUpload(false);
        setUploadSuccess("");
      }, 2000);
    } catch (err) {
      if (err.response?.status === 403) {
        setUploadError(
          "Session expired or insufficient permissions. Please log in again."
        );
      } else if (err.response?.status === 401) {
        setUploadError("Authentication failed. Please log in again.");
      } else {
        setUploadError(
          err.response?.data?.message || "Failed to upload images"
        );
      }
    } finally {
      setUploadLoading(false);
    }
  };

  // Delete image handler
  const handleDeleteImage = async (imageId) => {
    if (!isAuthenticated || !token) {
      setDeleteError("Authentication required. Please log in again.");
      return;
    }

    if (
      !window.confirm(
        "Are you sure you want to delete this image? This action cannot be undone."
      )
    ) {
      return;
    }

    setDeletingImageId(imageId);
    setDeleteError("");

    try {
      const config = {
        headers: {
          Authorization: `Bearer ${token}`,
        },
        withCredentials: true,
      };

      await axios.delete(
        `http://localhost:8080/api/inspections/images/${imageId}`,
        config
      );

      // Remove image from state
      const updatedMaintenanceImages = maintenanceImages.filter(
        (img) => img.id !== imageId
      );

      setInspection({
        ...inspection,
        images: updatedMaintenanceImages,
      });

      // Show success message
      setUploadSuccess("Image deleted successfully!");
      setTimeout(() => setUploadSuccess(""), 3000);
    } catch (err) {
      if (err.response?.status === 403) {
        setDeleteError("You don't have permission to delete this image.");
      } else if (err.response?.status === 401) {
        setDeleteError("Authentication failed. Please log in again.");
      } else {
        setDeleteError(err.response?.data?.message || "Failed to delete image");
      }
    } finally {
      setDeletingImageId(null);
    }
  };

  if (loading) {
    return (
      <div className="text-center mt-5">
        <Spinner animation="border" variant="primary" />
        <p className="mt-2">Loading inspection details...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="moodle-container">
        <Alert variant="danger" className="mt-4">
          <Alert.Heading>Access Error</Alert.Heading>
          <p>{error}</p>
          {!isAuthenticated && (
            <div className="mt-3">
              <Button variant="primary" onClick={() => navigate("/login")}>
                Go to Login
              </Button>
              <Button
                variant="outline-secondary"
                className="ms-2"
                onClick={() => navigate(-1)}
              >
                Go Back
              </Button>
            </div>
          )}
        </Alert>
      </div>
    );
  }

  if (!inspection) {
    return (
      <Alert variant="warning" className="mt-4">
        Inspection not found
      </Alert>
    );
  }

  return (
    <div className="moodle-container">
      <Button
        variant="outline-secondary"
        onClick={() => navigate(-1)}
        className="mb-3"
      >
        <FontAwesomeIcon icon={faArrowLeft} className="me-2" />
        Back to Inspections
      </Button>

      {/* Success/Error Alerts */}
      {uploadSuccess && (
        <Alert
          variant="success"
          dismissible
          onClose={() => setUploadSuccess("")}
        >
          {uploadSuccess}
        </Alert>
      )}
      {deleteError && (
        <Alert variant="danger" dismissible onClose={() => setDeleteError("")}>
          {deleteError}
        </Alert>
      )}

      {/* Debug Information - Remove in production */}
      {/* <Alert variant="info" className="mb-3">
        <strong>Debug Info:</strong> User: {authUser?.username} | Role: {authUser?.role} | 
        Can Delete: {canDeleteImages() ? 'YES' : 'NO'} | 
        Inspection Owner: {inspection.conductedByUser?.username || inspection.conductedBy?.username || 'Unknown'}
      </Alert> */}

      <Card className="mb-4">
        <Card.Body>
          <div className="d-flex justify-content-between align-items-start mb-4">
            <div>
              <h2>Inspection #{inspection.id}</h2>
              <div className="text-muted mb-3">
                <FontAwesomeIcon icon={faUser} className="me-2" />
                Conducted by: {inspection.conductedBy?.displayName || "Unknown"}
                {inspection.conductedByUser &&
                  ` (User: ${inspection.conductedByUser.username})`}
                {inspection.conductedByAdmin &&
                  ` (Admin: ${inspection.conductedByAdmin.username})`}
              </div>
              {inspection.inspectionDate && (
                <div className="text-muted mb-3">
                  <FontAwesomeIcon icon={faCalendar} className="me-2" />
                  Inspection Date:{" "}
                  {new Date(inspection.inspectionDate).toLocaleString()}
                </div>
              )}
              <div className="text-muted">
                <FontAwesomeIcon icon={faClock} className="me-2" />
                Added: {new Date(inspection.createdAt).toLocaleString()}
              </div>
            </div>
            <div>
              <Badge bg="info" className="fs-6 me-2">
                {allImages.length} Baseline Images
              </Badge>
              <Badge bg="success" className="fs-6">
                {maintenanceImages.length} Maintenance Images
              </Badge>
            </div>
          </div>

          <Row className="mb-4">
            <Col md={6}>
              <Card>
                <Card.Header>Transformer Details</Card.Header>
                <Card.Body>
                  <p>
                    <strong>Name:</strong>{" "}
                    {inspection.transformerRecord?.name || "Not specified"}
                  </p>
                  <p>
                    <strong>Location:</strong>{" "}
                    {inspection.transformerRecord?.locationName ||
                      "Not specified"}
                  </p>
                  <p>
                    <strong>Transformer Type:</strong>{" "}
                    {inspection.transformerRecord?.transformerType ||
                      "Not specified"}
                  </p>
                  <p>
                    <strong>Pole No:</strong>{" "}
                    {inspection.transformerRecord?.poleNo || "Not specified"}
                  </p>
                  <p>
                    <strong>Capacity:</strong>{" "}
                    {inspection.transformerRecord?.capacity
                      ? `${inspection.transformerRecord.capacity}kVA`
                      : "Not specified"}
                  </p>
                </Card.Body>
              </Card>
            </Col>
            <Col md={6}>
              <Card>
                <Card.Header>Inspection Notes</Card.Header>
                <Card.Body>
                  {inspection.notes ? (
                    <p>{inspection.notes}</p>
                  ) : (
                    <p className="text-muted">
                      No notes provided for this inspection.
                    </p>
                  )}
                </Card.Body>
              </Card>
            </Col>
          </Row>

          <Row className="mt-4">
            {/* Baseline Images - Left Side - SAME AS TransformerRecordDetail */}
            <Col md={6}>
              <h4 className="mb-3 text-center">Baseline Images</h4>
              {allImages.length > 0 ? (
                <Row className="g-3">
                  {allImages.map((image) => (
                    <Col key={image.id} xs={12}>
                      <Card className="h-100">
                        <div style={{ position: "relative" }}>
                          <Image
                            src={`http://localhost:8080${image.filePath}`}
                            alt={image.type}
                            fluid
                            style={{
                              height: "250px",
                              width: "100%",
                              objectFit: "cover",
                              cursor: "pointer",
                            }}
                            onClick={() => {
                              const imageIndex = allImages.findIndex(
                                (img) => img.id === image.id
                              );
                              openImageViewer(
                                allImages,
                                imageIndex,
                                "Baseline Images"
                              );
                            }}
                          />
                        </div>
                        <Card.Body>
                          <div className="d-flex justify-content-between align-items-start">
                            <div>
                              <strong>Type:</strong> {image.type}
                              {image.weatherCondition && (
                                <div>
                                  <strong>Weather:</strong>{" "}
                                  {image.weatherCondition}
                                </div>
                              )}
                            </div>
                            <div className="text-muted small text-end">
                              <FontAwesomeIcon
                                icon={faClock}
                                className="me-1"
                              />
                              {new Date(
                                image.uploadTime || image.createdAt
                              ).toLocaleString()}
                            </div>
                          </div>
                        </Card.Body>
                      </Card>
                    </Col>
                  ))}
                </Row>
              ) : (
                <Alert variant="info" className="text-center">
                  No Baseline images available
                </Alert>
              )}
            </Col>

            {/* Maintenance Images - Right Side */}
            <Col md={6}>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <h4 className="mb-0 text-center flex-grow-1">
                  Maintenance Images
                </h4>
                {isAuthenticated && (
                  <Button
                    variant="success"
                    size="sm"
                    onClick={() => setShowImageUpload(true)}
                  >
                    <FontAwesomeIcon icon={faPlus} className="me-2" />
                    Add Images
                  </Button>
                )}
              </div>
              {maintenanceImages.length > 0 ? (
                <Row className="g-3">
                  {maintenanceImages.map((image) => (
                    <Col key={image.id} xs={12}>
                      <Card className="h-100">
                        <div style={{ position: "relative" }}>
                          <Image
                            src={`http://localhost:8080${image.filePath}`}
                            alt={image.type}
                            fluid
                            style={{
                              height: "250px",
                              width: "100%",
                              objectFit: "cover",
                              cursor: "pointer",
                            }}
                            onClick={() => {
                              const imageIndex = maintenanceImages.findIndex(
                                (img) => img.id === image.id
                              );
                              openImageViewer(
                                maintenanceImages,
                                imageIndex,
                                "Maintenance Images"
                              );
                            }}
                          />
                          {/* Delete Button Overlay - USING SIMPLE VERSION FOR TESTING */}
                          {canDeleteImagesSimple() && (
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleDeleteImage(image.id);
                              }}
                              disabled={deletingImageId === image.id}
                              title="Delete this image"
                              style={{
                                position: "absolute",
                                top: "8px",
                                right: "8px",
                                width: "32px",
                                height: "32px",
                                borderRadius: "50%",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                backgroundColor: "rgba(33,37,41,0.65)", // subtle dark translucent
                                border: "1px solid rgba(255,255,255,0.8)",
                                boxShadow: "0 2px 6px rgba(0,0,0,0.35)",
                                backdropFilter: "blur(2px)",
                                cursor: "pointer",
                                transition:
                                  "background 0.15s ease, transform 0.1s ease, opacity 0.15s ease",
                                zIndex: 10,
                                color: "white",
                              }}
                              onMouseEnter={(e) =>
                                (e.currentTarget.style.backgroundColor =
                                  "#dc3545")
                              }
                              onMouseLeave={(e) =>
                                (e.currentTarget.style.backgroundColor =
                                  "rgba(33,37,41,0.65)")
                              }
                            >
                              {deletingImageId === image.id ? (
                                <Spinner
                                  as="span"
                                  animation="border"
                                  size="sm"
                                />
                              ) : (
                                <FontAwesomeIcon
                                  icon={faTrashCan}
                                  style={{ fontSize: "14px" }}
                                />
                              )}
                            </button>
                          )}
                        </div>
                        <Card.Body>
                          <div className="d-flex justify-content-between align-items-start">
                            <div>
                              <strong>Type:</strong> {image.type}
                            </div>
                            <div className="text-muted small text-end">
                              <FontAwesomeIcon
                                icon={faClock}
                                className="me-1"
                              />
                              {new Date(
                                image.uploadTime || image.createdAt
                              ).toLocaleString()}
                            </div>
                          </div>
                        </Card.Body>
                      </Card>
                    </Col>
                  ))}
                </Row>
              ) : (
                <Alert variant="info" className="text-center">
                  No Maintenance images available for this inspection
                </Alert>
              )}
            </Col>
          </Row>
        </Card.Body>
      </Card>

      {/* Main Content Tabs */}
      <Card className="mb-4">
        <Card.Body>
          <Tabs
            activeKey={activeTab}
            onSelect={(k) => setActiveTab(k)}
            className="mb-3"
          >
            {/* Images & Analysis Tab */}
            <Tab
              eventKey="images"
              title={
                <>
                  <FontAwesomeIcon icon={faImages} className="me-2" />
                  Images & Analysis
                </>
              }
            >
              {/* Anomaly Analysis Section */}
              <AnalysisDisplay
                inspectionId={inspection.id}
                images={maintenanceImages}
              />
            </Tab>

            {/* Maintenance Record Form Tab */}
            <Tab
              eventKey="maintenance"
              title={
                <>
                  <FontAwesomeIcon icon={faFileAlt} className="me-2" />
                  Maintenance Record
                </>
              }
            >
              <MaintenanceRecordForm
                inspectionId={inspection.id}
                inspection={inspection}
              />
            </Tab>

            {/* Maintenance History Tab */}
            <Tab
              eventKey="history"
              title={
                <>
                  <FontAwesomeIcon icon={faHistory} className="me-2" />
                  Maintenance History
                </>
              }
            >
              {transformerRecord?.id ? (
                <MaintenanceRecordsHistory
                  transformerId={transformerRecord.id}
                />
              ) : (
                <Alert variant="info">
                  <FontAwesomeIcon
                    icon={faExclamationTriangle}
                    className="me-2"
                  />
                  No transformer record associated with this inspection.
                </Alert>
              )}
            </Tab>
          </Tabs>
        </Card.Body>
      </Card>

      {/* Image Upload Modal */}
      <Modal
        show={showImageUpload}
        onHide={() => {
          setShowImageUpload(false);
          setNewImages([]);
          setUploadError("");
          setUploadSuccess("");
        }}
        size="lg"
        centered
      >
        <Modal.Header closeButton>
          <Modal.Title>
            <FontAwesomeIcon icon={faUpload} className="me-2" />
            Add Images to Inspection
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {uploadError && (
            <Alert
              variant="danger"
              dismissible
              onClose={() => setUploadError("")}
            >
              {uploadError}
            </Alert>
          )}
          {uploadSuccess && (
            <Alert
              variant="success"
              dismissible
              onClose={() => setUploadSuccess("")}
            >
              {uploadSuccess}
            </Alert>
          )}

          <p className="text-muted mb-3">
            Upload additional maintenance images for this inspection. These
            images will be associated with the inspection and visible in the
            maintenance images section.
          </p>

          {newImages.length === 0 && (
            <Alert variant="info">
              No images selected yet. Click "Add Image" below to select images
              to upload.
            </Alert>
          )}

          {newImages.map((img, index) => (
            <Card key={index} className="mb-3">
              <Card.Body>
                <Row>
                  <Col md={10}>
                    <Form.Group>
                      <Form.Label>
                        Maintenance Image {index + 1} {img ? "(Selected)" : ""}
                      </Form.Label>
                      <Form.Control
                        type="file"
                        accept="image/*"
                        onChange={(e) =>
                          handleImageChange(index, e.target.files[0])
                        }
                      />
                    </Form.Group>
                  </Col>
                  <Col md={2} className="d-flex align-items-end">
                    <Button
                      variant="danger"
                      onClick={() => removeImageField(index)}
                      className="w-100"
                    >
                      <FontAwesomeIcon icon={faTimes} />
                    </Button>
                  </Col>
                </Row>
              </Card.Body>
            </Card>
          ))}

          <div className="text-center">
            <Button variant="outline-primary" onClick={addImageField}>
              <FontAwesomeIcon icon={faPlus} className="me-2" />
              Add Image
            </Button>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            onClick={() => {
              setShowImageUpload(false);
              setNewImages([]);
              setUploadError("");
              setUploadSuccess("");
            }}
          >
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={handleImageUpload}
            disabled={
              uploadLoading ||
              newImages.filter((img) => img !== null).length === 0
            }
          >
            {uploadLoading ? (
              <>
                <Spinner
                  as="span"
                  animation="border"
                  size="sm"
                  className="me-2"
                />
                Uploading...
              </>
            ) : (
              <>
                <FontAwesomeIcon icon={faUpload} className="me-2" />
                Upload Images
              </>
            )}
          </Button>
        </Modal.Footer>
      </Modal>

      {/* Advanced Image Viewer */}
      <ImageViewer
        show={showImageViewer}
        onHide={() => setShowImageViewer(false)}
        images={viewerImages}
        currentIndex={viewerCurrentIndex}
        title={viewerTitle}
      />
    </div>
  );
};

export default InspectionDetail;
