# Maintenance Record System Documentation

## Overview
This document describes the comprehensive maintenance record system implemented for transformer inspections. The system allows engineers to fill out detailed maintenance forms, save them to the database, and retrieve historical records.

## Features Implemented

### FR4.1: Generate Maintenance Record Form
Each inspection now includes an automated maintenance record form that captures:

#### Transformer Metadata (Auto-populated, Read-only)
- Transformer ID
- Transformer Name
- Location
- Capacity (kVA)
- Type
- Inspection Timestamp
- Inspector (who conducted the inspection)

#### Anomaly Information
- List of detected anomalies from thermal analysis
- Anomaly locations and details
- System-generated anomaly data from ML model

### FR4.2: Editable Engineer Input Fields

The maintenance form is organized into **5 tabbed sections**:

#### Tab 1: Inspector Information
- **Inspector Name** (required)
- **Inspector ID**
- **Inspector Email**
- **Transformer Status** (required dropdown):
  - OK - Normal Operation
  - Needs Maintenance
  - Urgent Attention Required

#### Tab 2: Electrical Readings
- **Voltage Readings** (Phase A, B, C in Volts)
- **Current Readings** (Phase A, B, C in Amperes)
- **Power Factor** (0.0 - 1.0)
- **Frequency** (Hz)
- **Load Condition** (dropdown):
  - No Load
  - Light Load
  - Normal Load
  - Heavy Load
  - Overload
- **Temperature Readings**:
  - Ambient Temperature (°C)
  - Oil Temperature (°C)
  - Winding Temperature (°C)

#### Tab 3: Visual Inspection
- **Oil Level** (dropdown): Normal, Low, Critical
- **Oil Color** (dropdown): Clear, Light Brown, Dark Brown, Black
- **Oil Analysis Remarks** (text area)
- **Cooling System Condition** (text area)
- **Bushing Condition** (text area)
- **Tank Condition** (text area)
- **Gauges and Indicators Condition** (text area)
- **Weather Condition** (dropdown): Clear, Cloudy, Rainy, Stormy

#### Tab 4: Maintenance Actions
- **Detected Anomalies** (text area) - List all detected issues
- **Corrective Actions Taken** (text area)
- **Recommended Action** (text area, required)
- **Maintenance Priority** (dropdown):
  - Low
  - Medium
  - High
  - Critical
- **Scheduled Maintenance Date** (datetime picker)
- **Parts Replaced** (text area)
- **Materials Used** (text area)

#### Tab 5: Follow-up & Notes
- **Requires Follow-up** (checkbox)
- **Follow-up Date** (datetime picker, shown if follow-up required)
- **Follow-up Notes** (text area)
- **Engineer Notes** (text area)
- **Additional Remarks** (text area)
- **Safety Observations** (text area)
- **Compliance Check Completed** (checkbox)
- **Compliance Notes** (text area)

### Field Types Supported
- **Text Inputs**: Single-line text fields for names, IDs, emails
- **Number Inputs**: For electrical readings with decimal precision
- **Dropdowns**: For standardized selections (status, priority, oil level, etc.)
- **Date/Time Pickers**: For scheduling and timestamp fields
- **Text Areas**: For detailed notes and observations
- **Checkboxes**: For boolean flags (compliance, follow-up required)

### Clear Separation
- **System-Generated Content** (read-only):
  - Displayed in a gray/light background card at the top
  - Includes transformer metadata and inspection details
  
- **Editable Fields**:
  - Organized in tabbed interface for easy navigation
  - All input fields are clearly labeled and editable
  - Required fields marked with asterisks (*)

### FR4.3: Save and Retrieve Completed Records

#### Save Functionality
- **Save as Draft**: Saves form data without submission (status: DRAFT)
- **Submit Record**: Submits the record for review (status: SUBMITTED)
- Both actions preserve all form data in the database

#### Database Storage
Each maintenance record includes:
- **Association**: Linked to specific inspection and transformer
- **Timestamps**: Created and updated timestamps
- **User Tracking**: Tracks who created/submitted the record
- **Status Tracking**: DRAFT → SUBMITTED → REVIEWED → APPROVED
- **Version Control**: Updates existing record or creates new one

#### Record Retrieval

##### 1. Current Inspection Record
- Automatically loads existing record when viewing inspection
- Shows current status badge (DRAFT, SUBMITTED, etc.)
- All fields pre-populated with saved data

##### 2. Maintenance Records History Component
Available at the transformer level showing:
- **Table View** with columns:
  - Date of inspection
  - Inspector name
  - Transformer status
  - Maintenance priority
  - Recommended action (truncated)
  - Record status
  - View action button

- **Detailed Modal View** showing:
  - All inspector information
  - Complete electrical readings
  - All visual inspection data
  - Maintenance actions and recommendations
  - Parts and materials used
  - Follow-up requirements
  - Safety and compliance information
  - Record metadata (created, updated, reviewed dates)

##### 3. Filtering and Searching
Backend API endpoints support:
- Get all records for a specific transformer
- Filter by transformer status
- Filter by maintenance priority
- Get pending follow-ups across all transformers
- Get all maintenance records (admin view)

## Backend API Endpoints

### Create/Update Maintenance Record
```
POST /api/maintenance-records/inspection/{inspectionId}
```
- Creates new record or updates existing one
- Authenticated users (ADMIN or USER roles)
- Request body: JSON with all maintenance record fields

### Get Maintenance Record by Inspection
```
GET /api/maintenance-records/inspection/{inspectionId}
```
- Retrieves record for specific inspection
- Returns 404 if no record exists

### Get Maintenance Records by Transformer
```
GET /api/maintenance-records/transformer/{transformerId}
```
- Returns all maintenance records for a transformer
- Ordered by creation date (newest first)

### Get All Maintenance Records
```
GET /api/maintenance-records
```
- Returns all maintenance records in the system
- For admin overview/reporting

### Filter by Status
```
GET /api/maintenance-records/status/{status}
```
- Filter records by transformer status
- Status values: OK, NEEDS_MAINTENANCE, URGENT_ATTENTION

### Filter by Priority
```
GET /api/maintenance-records/priority/{priority}
```
- Filter records by maintenance priority
- Priority values: LOW, MEDIUM, HIGH, CRITICAL

### Get Pending Follow-ups
```
GET /api/maintenance-records/pending-followups
```
- Returns records requiring follow-up
- Ordered by follow-up date

### Submit Record
```
PUT /api/maintenance-records/{id}/submit
```
- Changes status from DRAFT to SUBMITTED
- Authenticated users

### Review Record (Admin Only)
```
PUT /api/maintenance-records/{id}/review?status={status}
```
- Admin can mark as REVIEWED or APPROVED
- Records who reviewed and when

### Delete Record (Admin Only)
```
DELETE /api/maintenance-records/{id}
```
- Permanently delete a maintenance record

## Database Schema

### MaintenanceRecord Entity
**Table**: `maintenance_records`

**Key Fields**:
- `id` (Primary Key)
- `inspection_id` (Foreign Key, unique)
- Inspector info (name, ID, email)
- Transformer status
- Electrical readings (voltage, current, power factor, etc.)
- Temperature readings
- Oil analysis data
- Visual inspection notes
- Maintenance actions
- Parts and materials
- Follow-up information
- Safety and compliance data
- Status tracking
- User relationships (submitted by, reviewed by)
- Timestamps (created, updated, reviewed)

**Relationships**:
- One-to-One with Inspection
- Many-to-One with User (submitter)
- Many-to-One with Admin (submitter/reviewer)

## Frontend Components

### 1. MaintenanceRecordForm.js
**Location**: `src/components/MaintenanceRecordForm.js`

**Features**:
- Tabbed interface (5 tabs)
- Auto-load existing records
- Auto-save functionality
- Draft and submit options
- Form validation
- Success/error notifications

**Props**:
- `inspectionId`: ID of the inspection
- `inspection`: Full inspection object (for metadata)

### 2. MaintenanceRecordsHistory.js
**Location**: `src/components/MaintenanceRecordsHistory.js`

**Features**:
- Table view of all records
- Color-coded status badges
- Priority indicators
- Detailed modal view
- Responsive design

**Props**:
- `transformerId`: ID of the transformer

### 3. Integration with InspectionDetail.js
The maintenance form is automatically displayed on the inspection detail page:
1. Transformer metadata (read-only)
2. Images (baseline and maintenance)
3. Anomaly analysis results
4. **Maintenance Record Form** (new)

## User Workflow

### Creating a Maintenance Record

1. **Navigate to Inspection**:
   - Go to transformer record
   - Select an inspection

2. **View Analysis**:
   - Review anomaly detection results
   - Check thermal images with markers

3. **Fill Maintenance Form**:
   - Complete inspector information
   - Enter electrical readings
   - Document visual inspection
   - Record maintenance actions
   - Add notes and recommendations

4. **Save Progress**:
   - Click "Save as Draft" to preserve work
   - Form can be edited later

5. **Submit Record**:
   - Click "Submit Record" when complete
   - Status changes to SUBMITTED

### Viewing Historical Records

1. **From Transformer Record Page**:
   - Add `<MaintenanceRecordsHistory transformerId={id} />` component
   - Shows all past maintenance records

2. **View Details**:
   - Click "View" button on any record
   - Opens detailed modal with all information

3. **Filter/Search** (via API):
   - Filter by status, priority
   - Find pending follow-ups

## Admin Features

### Review and Approval
Admins can:
- Review submitted records
- Mark records as REVIEWED or APPROVED
- Add review notes
- Delete records if needed

### Reporting
Access to:
- All maintenance records across all transformers
- Filter by status/priority
- Pending follow-ups system-wide

## Security

### Authentication Required
- All endpoints require Bearer token
- Role-based access control (ADMIN/USER)

### Authorization
- Users can create/edit records for their inspections
- Users can view all records
- Only admins can delete records
- Only admins can review/approve records

### Data Validation
- Required fields enforced on backend
- Type checking for numerical values
- Enum validation for dropdown values

## Best Practices

### For Engineers
1. Fill inspector information first (Tab 1)
2. Record all electrical readings while on-site (Tab 2)
3. Document visual observations immediately (Tab 3)
4. List all actions taken (Tab 4)
5. Add detailed notes before submission (Tab 5)
6. Save as draft frequently to avoid data loss
7. Submit record only when complete

### For Administrators
1. Review submitted records promptly
2. Check for completeness before approval
3. Monitor pending follow-ups regularly
4. Use filtering to identify critical issues
5. Generate reports for compliance

## Future Enhancements

Potential additions:
- PDF export of maintenance records
- Email notifications for follow-ups
- Dashboard with statistics
- Trend analysis across records
- Mobile-optimized interface
- Offline capability
- Digital signature support
- Photo attachment to specific fields
- Integration with work order systems
- Automated reminder system for follow-ups

## Technical Notes

### Database Migration
On first deployment, the database will automatically create the `maintenance_records` table with all required columns. Ensure your application.properties has:
```properties
spring.jpa.hibernate.ddl-auto=update
```

### CORS Configuration
Backend controller includes CORS for `http://localhost:3000`. Update for production environment.

### Data Persistence
All form data is persisted on each save. No data loss on page refresh if saved as draft.

### Performance Considerations
- Records are fetched per inspection (not all at once)
- History component loads all records for a transformer
- Consider pagination for transformers with many records
- Indexes on `inspection_id` and foreign keys for fast retrieval

## Summary

This comprehensive maintenance record system fulfills all requirements:

✅ **FR4.1**: Generates maintenance record forms with transformer metadata and anomaly data  
✅ **FR4.2**: Provides extensive editable fields organized in tabs with dropdowns, dates, and text inputs  
✅ **FR4.3**: Saves records to database with full retrieval and history viewing capabilities  

The system is production-ready and provides a complete workflow from inspection to maintenance documentation and historical tracking.
