import React, { useState, useEffect } from "react";
import axios from "axios";
import {
  Card,
  Button,
  Table,
  Spinner,
  Alert,
  Row,
  Col,
  Modal,
  Badge,
  Pagination,
  Form,
  InputGroup,
  Dropdown,
  ButtonGroup,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faEye,
  faTrash,
  faEdit,
  faPlus,
  faMapMarkerAlt,
  faImages,
  faSearch,
  faFilter,
  faSort,
  faCalendarAlt,
  faUser,
  faList,
  faThLarge,
  faBolt,
  faHashtag,
  faStar,
} from "@fortawesome/free-solid-svg-icons";
import { faStar as faStarRegular } from "@fortawesome/free-regular-svg-icons";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";

const TransformerList = () => {
  const [transformerRecords, setTransformerRecords] = useState([]);
  const [filteredRecords, setFilteredRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [recordToDelete, setRecordToDelete] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage] = useState(6);
  const [searchTerm, setSearchTerm] = useState("");
  const [searchField, setSearchField] = useState("name");
  const [sortConfig, setSortConfig] = useState({
    key: "name",
    direction: "asc",
  });
  const [capacityFilter, setCapacityFilter] = useState("all");
  const [starredFilter, setStarredFilter] = useState("all"); // 'all', 'starred', 'unstarred'
  const [viewMode, setViewMode] = useState("cards"); // 'cards' or 'table'

  const navigate = useNavigate();
  const { token, isAuthenticated, isAdmin } = useAuth();

  const toggleStar = async (recordId, currentStarred) => {
    try {
      const config = {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      };
      await axios.patch(
        `http://localhost:8080/api/transformer-records/${recordId}/toggle-star`,
        {},
        config
      );
      // Refresh the records to show updated star status
      fetchTransformerRecords();
    } catch (err) {
      setError("Failed to update star status");
    }
  };

  useEffect(() => {
    fetchTransformerRecords();
  }, [token]);

  const fetchTransformerRecords = async () => {
    try {
      setLoading(true);
      const config = {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      };
      const response = await axios.get(
        "http://localhost:8080/api/transformer-records",
        config
      );
      setTransformerRecords(response.data);
      setFilteredRecords(response.data);
      setError("");
    } catch (err) {
      setError("Failed to fetch transformer records");
      setTransformerRecords([]);
      setFilteredRecords([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let result = [...transformerRecords];

    // Apply search filter
    if (searchTerm) {
      result = result.filter((record) => {
        if (searchField === "name") {
          return record.name.toLowerCase().includes(searchTerm.toLowerCase());
        } else if (searchField === "location") {
          return record.locationName
            ?.toLowerCase()
            .includes(searchTerm.toLowerCase());
        } else if (searchField === "admin") {
          return record.uploadedBy?.displayName
            ?.toLowerCase()
            .includes(searchTerm.toLowerCase());
        } else if (searchField === "transformerType") {
          return record.transformerType
            ?.toLowerCase()
            .includes(searchTerm.toLowerCase());
        } else if (searchField === "poleNo") {
          return record.poleNo?.toString().includes(searchTerm.toLowerCase());
        }
        return true;
      });
    }

    // Apply capacity filter
    if (capacityFilter !== "all") {
      result = result.filter((record) => {
        if (capacityFilter === "small") {
          return record.capacity && record.capacity < 50;
        } else if (capacityFilter === "medium") {
          return (
            record.capacity && record.capacity >= 50 && record.capacity < 200
          );
        } else if (capacityFilter === "large") {
          return record.capacity && record.capacity >= 200;
        }
        return true;
      });
    }

    // Apply starred filter
    if (starredFilter !== "all") {
      result = result.filter((record) => {
        if (starredFilter === "starred") {
          return record.starred === true;
        } else if (starredFilter === "unstarred") {
          return record.starred !== true;
        }
        return true;
      });
    }

    // Apply sorting
    if (sortConfig.key) {
      result.sort((a, b) => {
        if (a[sortConfig.key] < b[sortConfig.key]) {
          return sortConfig.direction === "asc" ? -1 : 1;
        }
        if (a[sortConfig.key] > b[sortConfig.key]) {
          return sortConfig.direction === "asc" ? 1 : -1;
        }
        return 0;
      });
    }

    setFilteredRecords(result);
    setCurrentPage(1); // Reset to first page when filters change
  }, [
    transformerRecords,
    searchTerm,
    searchField,
    capacityFilter,
    starredFilter,
    sortConfig,
  ]);

  const handleDelete = async (id) => {
    try {
      await axios.delete(
        `http://localhost:8080/api/transformer-records/${id}`,
        {
          headers: { Authorization: `Bearer ${token}` },
        }
      );
      fetchTransformerRecords();
    } catch (err) {
      setError("Failed to delete transformer record");
    } finally {
      setShowDeleteModal(false);
    }
  };

  const requestSort = (key) => {
    let direction = "asc";
    if (sortConfig.key === key && sortConfig.direction === "asc") {
      direction = "desc";
    }
    setSortConfig({ key, direction });
  };

  // Pagination logic
  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentItems = filteredRecords.slice(indexOfFirstItem, indexOfLastItem);
  const totalPages = Math.ceil(filteredRecords.length / itemsPerPage);

  if (loading) {
    return (
      <div className="text-center mt-5">
        <Spinner animation="border" variant="primary" />
        <p className="mt-2">Loading transformer records...</p>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="danger" className="mt-4">
        {error}
      </Alert>
    );
  }

  return (
    <div className="moodle-container">
      <div className="page-header">
        <h2>Transformers</h2>
        <div className="d-flex align-items-center">
          <ButtonGroup className="me-3">
            <Button
              variant={viewMode === "cards" ? "primary" : "outline-secondary"}
              onClick={() => setViewMode("cards")}
              size="sm"
            >
              <FontAwesomeIcon icon={faThLarge} />
            </Button>
            <Button
              variant={viewMode === "table" ? "primary" : "outline-secondary"}
              onClick={() => setViewMode("table")}
              size="sm"
            >
              <FontAwesomeIcon icon={faList} />
            </Button>
          </ButtonGroup>
          {isAuthenticated && (
            <Button variant="primary" onClick={() => navigate("/upload")}>
              <FontAwesomeIcon icon={faPlus} className="me-2" />
              Add New Transformer
            </Button>
          )}
        </div>
      </div>

      {/* Search and Filter Bar */}
      <Card className="mb-4">
        <Card.Body>
          <Row className="g-3">
            <Col md={6}>
              <InputGroup>
                <Dropdown>
                  <Dropdown.Toggle variant="outline-secondary">
                    {searchField === "name" && "Name"}
                    {searchField === "location" && "Location"}
                    {searchField === "admin" && "Admin"}
                    {searchField === "transformerType" && "Type"}
                    {searchField === "poleNo" && "Pole No"}
                  </Dropdown.Toggle>
                  <Dropdown.Menu>
                    <Dropdown.Item onClick={() => setSearchField("name")}>
                      Name
                    </Dropdown.Item>
                    <Dropdown.Item onClick={() => setSearchField("location")}>
                      Location
                    </Dropdown.Item>
                    <Dropdown.Item onClick={() => setSearchField("admin")}>
                      Admin
                    </Dropdown.Item>
                    <Dropdown.Item
                      onClick={() => setSearchField("transformerType")}
                    >
                      Type
                    </Dropdown.Item>
                    <Dropdown.Item onClick={() => setSearchField("poleNo")}>
                      Pole No
                    </Dropdown.Item>
                  </Dropdown.Menu>
                </Dropdown>
                <Form.Control
                  type="text"
                  placeholder={`Search by ${searchField}...`}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
                <Button variant="outline-secondary">
                  <FontAwesomeIcon icon={faSearch} />
                </Button>
              </InputGroup>
            </Col>
            <Col md={2}>
              <Dropdown>
                <Dropdown.Toggle variant="outline-secondary">
                  <FontAwesomeIcon icon={faFilter} className="me-2" />
                  Capacity: {capacityFilter === "all" && "All"}
                  {capacityFilter === "small" && "Small (<50kVA)"}
                  {capacityFilter === "medium" && "Medium (50-200kVA)"}
                  {capacityFilter === "large" && "Large (200+kVA)"}
                </Dropdown.Toggle>
                <Dropdown.Menu>
                  <Dropdown.Item onClick={() => setCapacityFilter("all")}>
                    All
                  </Dropdown.Item>
                  <Dropdown.Item onClick={() => setCapacityFilter("small")}>
                    Small (&lt;50kVA)
                  </Dropdown.Item>
                  <Dropdown.Item onClick={() => setCapacityFilter("medium")}>
                    Medium (50-200kVA)
                  </Dropdown.Item>
                  <Dropdown.Item onClick={() => setCapacityFilter("large")}>
                    Large (200+kVA)
                  </Dropdown.Item>
                </Dropdown.Menu>
              </Dropdown>
            </Col>
            <Col md={2}>
              <Dropdown>
                <Dropdown.Toggle variant="outline-secondary">
                  <FontAwesomeIcon icon={faStar} className="me-2" />
                  Stars: {starredFilter === "all" && "All"}
                  {starredFilter === "starred" && "Starred"}
                  {starredFilter === "unstarred" && "Unstarred"}
                </Dropdown.Toggle>
                <Dropdown.Menu>
                  <Dropdown.Item onClick={() => setStarredFilter("all")}>
                    All
                  </Dropdown.Item>
                  <Dropdown.Item onClick={() => setStarredFilter("starred")}>
                    Starred Only
                  </Dropdown.Item>
                  <Dropdown.Item onClick={() => setStarredFilter("unstarred")}>
                    Unstarred Only
                  </Dropdown.Item>
                </Dropdown.Menu>
              </Dropdown>
            </Col>
            <Col md={2}>
              <Dropdown>
                <Dropdown.Toggle variant="outline-secondary">
                  <FontAwesomeIcon icon={faSort} className="me-2" />
                  Sort: {sortConfig.key === "name" && "Name"}
                  {sortConfig.key === "createdAt" && "Date"}
                  {sortConfig.key === "capacity" && "Capacity"}
                  {sortConfig.direction === "asc" ? "↑" : "↓"}
                </Dropdown.Toggle>
                <Dropdown.Menu>
                  <Dropdown.Item onClick={() => requestSort("name")}>
                    Name{" "}
                    {sortConfig.key === "name" &&
                      (sortConfig.direction === "asc" ? "↑" : "↓")}
                  </Dropdown.Item>
                  <Dropdown.Item onClick={() => requestSort("createdAt")}>
                    Date{" "}
                    {sortConfig.key === "createdAt" &&
                      (sortConfig.direction === "asc" ? "↑" : "↓")}
                  </Dropdown.Item>
                  <Dropdown.Item onClick={() => requestSort("capacity")}>
                    Capacity{" "}
                    {sortConfig.key === "capacity" &&
                      (sortConfig.direction === "asc" ? "↑" : "↓")}
                  </Dropdown.Item>
                </Dropdown.Menu>
              </Dropdown>
            </Col>
          </Row>
        </Card.Body>
      </Card>

      {filteredRecords.length === 0 ? (
        <div className="text-center py-4">
          <FontAwesomeIcon
            icon={faImages}
            size="3x"
            className="text-muted mb-3"
          />
          <p>No transformer records found</p>
          {searchTerm && (
            <Button
              variant="link"
              onClick={() => {
                setSearchTerm("");
                setCapacityFilter("all");
                setStarredFilter("all");
              }}
            >
              Clear filters
            </Button>
          )}
        </div>
      ) : viewMode === "table" ? (
        <>
          <Card className="mb-4">
            <Card.Body>
              <Table striped hover responsive className="moodle-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Location</th>
                    <th>Type</th>
                    <th>Pole No</th>
                    <th>Capacity</th>
                    <th>Images</th>
                    <th>Admin</th>
                    <th>Date</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {currentItems.map((record) => (
                    <tr key={record.id}>
                      <td>{record.name}</td>
                      <td>
                        {record.locationName && (
                          <>
                            <FontAwesomeIcon
                              icon={faMapMarkerAlt}
                              className="me-2 text-muted"
                            />
                            {record.locationName
                              .split(",")
                              .slice(0, 2)
                              .map((part) => part.trim())
                              .join(", ")}
                          </>
                        )}
                      </td>
                      <td>
                        {record.transformerType ? (
                          <Badge bg="info">{record.transformerType}</Badge>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td>
                        {record.poleNo ? (
                          <Badge bg="secondary">{record.poleNo}</Badge>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td>
                        {record.capacity ? (
                          <Badge
                            bg={
                              record.capacity < 50
                                ? "info"
                                : record.capacity < 200
                                ? "primary"
                                : "success"
                            }
                          >
                            {record.capacity}kVA
                          </Badge>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td>
                        <Badge bg="secondary">
                          {record.images?.length || 0}
                        </Badge>
                      </td>
                      <td>{record.uploadedBy?.displayName || "-"}</td>
                      <td>{new Date(record.createdAt).toLocaleDateString()}</td>
                      <td>
                        <Button
                          variant={
                            record.starred ? "warning" : "outline-warning"
                          }
                          size="sm"
                          onClick={() => toggleStar(record.id, record.starred)}
                          className="me-2"
                          title={record.starred ? "Unstar" : "Star"}
                        >
                          <FontAwesomeIcon
                            icon={record.starred ? faStar : faStarRegular}
                          />
                        </Button>
                        <Button
                          variant="outline-primary"
                          size="sm"
                          onClick={() => navigate(`/records/${record.id}`)}
                          className="me-2"
                        >
                          <FontAwesomeIcon icon={faEye} />
                        </Button>
                        {isAdmin && (
                          <>
                            <Button
                              variant="outline-secondary"
                              size="sm"
                              onClick={() =>
                                navigate(`/upload?edit=${record.id}`)
                              }
                              className="me-2"
                            >
                              <FontAwesomeIcon icon={faEdit} />
                            </Button>
                            <Button
                              variant="outline-danger"
                              size="sm"
                              onClick={() => {
                                setRecordToDelete(record.id);
                                setShowDeleteModal(true);
                              }}
                            >
                              <FontAwesomeIcon icon={faTrash} />
                            </Button>
                          </>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </Card.Body>
          </Card>
        </>
      ) : (
        <>
          <Row xs={1} md={2} lg={3} className="g-4">
            {currentItems.map((record) => (
              <Col key={record.id}>
                <Card className="h-100 bg-light">
                  <Card.Body>
                    <Card.Title className="d-flex justify-content-between align-items-start">
                      {record.name}
                      <Badge
                        bg={
                          record.capacity < 50
                            ? "info"
                            : record.capacity < 200
                            ? "primary"
                            : "success"
                        }
                        className="ms-2"
                      >
                        {record.capacity || 0}kVA
                      </Badge>
                    </Card.Title>

                    {record.locationName && (
                      <Card.Text className="text-muted mb-2">
                        <FontAwesomeIcon
                          icon={faMapMarkerAlt}
                          className="me-2"
                        />
                        {record.locationName
                          .split(",")
                          .slice(0, 3)
                          .map((part) => part.trim())
                          .join(", ")}
                      </Card.Text>
                    )}

                    <div className="d-flex justify-content-between text-muted mb-3">
                      <div>
                        {record.transformerType && (
                          <div className="mb-1">
                            <FontAwesomeIcon icon={faBolt} className="me-2" />
                            <small>{record.transformerType}</small>
                          </div>
                        )}
                        {record.poleNo && (
                          <div>
                            <FontAwesomeIcon
                              icon={faHashtag}
                              className="me-2"
                            />
                            <small>Pole #{record.poleNo}</small>
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="d-flex justify-content-between text-muted mb-3">
                      <small>
                        <FontAwesomeIcon icon={faUser} className="me-2" />
                        {record.uploadedBy?.displayName || "Unknown"}
                      </small>
                      <small>
                        <FontAwesomeIcon
                          icon={faCalendarAlt}
                          className="me-2"
                        />
                        {new Date(record.createdAt).toLocaleDateString()}
                      </small>
                    </div>

                    <div className="d-flex justify-content-between align-items-center">
                      <Badge bg="secondary">
                        {record.images?.length || 0} images
                      </Badge>

                      <div>
                        <Button
                          variant={
                            record.starred ? "warning" : "outline-warning"
                          }
                          size="sm"
                          onClick={() => toggleStar(record.id, record.starred)}
                          className="me-2"
                          title={record.starred ? "Unstar" : "Star"}
                        >
                          <FontAwesomeIcon
                            icon={record.starred ? faStar : faStarRegular}
                          />
                        </Button>
                        <Button
                          variant="outline-primary"
                          size="sm"
                          onClick={() => navigate(`/records/${record.id}`)}
                          className="me-2"
                        >
                          <FontAwesomeIcon icon={faEye} />
                        </Button>
                        {isAdmin && (
                          <>
                            <Button
                              variant="outline-secondary"
                              size="sm"
                              onClick={() =>
                                navigate(`/upload?edit=${record.id}`)
                              }
                              className="me-2"
                            >
                              <FontAwesomeIcon icon={faEdit} />
                            </Button>
                            <Button
                              variant="outline-danger"
                              size="sm"
                              onClick={() => {
                                setRecordToDelete(record.id);
                                setShowDeleteModal(true);
                              }}
                            >
                              <FontAwesomeIcon icon={faTrash} />
                            </Button>
                          </>
                        )}
                      </div>
                    </div>
                  </Card.Body>
                </Card>
              </Col>
            ))}
          </Row>
        </>
      )}

      {totalPages > 1 && (
        <div className="d-flex justify-content-center mt-4">
          <Pagination>
            <Pagination.First
              onClick={() => setCurrentPage(1)}
              disabled={currentPage === 1}
            />
            <Pagination.Prev
              onClick={() => setCurrentPage(currentPage - 1)}
              disabled={currentPage === 1}
            />
            {[...Array(totalPages).keys()].map((number) => {
              const page = number + 1;
              // Show only first, last, and pages around current
              if (
                page === 1 ||
                page === totalPages ||
                (page >= currentPage - 1 && page <= currentPage + 1)
              ) {
                return (
                  <Pagination.Item
                    key={page}
                    active={page === currentPage}
                    onClick={() => setCurrentPage(page)}
                  >
                    {page}
                  </Pagination.Item>
                );
              } else if (
                (page === currentPage - 2 && currentPage > 3) ||
                (page === currentPage + 2 && currentPage < totalPages - 2)
              ) {
                return <Pagination.Ellipsis key={page} />;
              }
              return null;
            })}
            <Pagination.Next
              onClick={() => setCurrentPage(currentPage + 1)}
              disabled={currentPage === totalPages}
            />
            <Pagination.Last
              onClick={() => setCurrentPage(totalPages)}
              disabled={currentPage === totalPages}
            />
          </Pagination>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      <Modal show={showDeleteModal} onHide={() => setShowDeleteModal(false)}>
        <Modal.Header closeButton>
          <Modal.Title>Confirm Deletion</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          Are you sure you want to delete this transformer record? This action
          cannot be undone.
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setShowDeleteModal(false)}>
            Cancel
          </Button>
          <Button variant="danger" onClick={() => handleDelete(recordToDelete)}>
            Delete
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
};

export default TransformerList;
