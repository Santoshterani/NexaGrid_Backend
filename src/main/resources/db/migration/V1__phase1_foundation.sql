-- Phase 1: identity, tenancy roots, sessions and audit.
-- Later phases add inventory, plans, billing, tickets (see telecom_platform_schema.sql).
CREATE TABLE operators (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(20) NOT NULL,
  name VARCHAR(150) NOT NULL,
  status ENUM('ACTIVE','SUSPENDED','PENDING_APPROVAL') NOT NULL DEFAULT 'ACTIVE',
  max_plan_price DECIMAL(12,2) NOT NULL,
  contact_email VARCHAR(190) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_operators_code (code),
  UNIQUE KEY uq_operators_name (name),
  CONSTRAINT ck_operators_cap CHECK (max_plan_price > 0)
) ENGINE=InnoDB;

CREATE TABLE users (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT UNSIGNED NULL,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL,
  role ENUM('SUPER_ADMIN','OPERATOR_ADMIN','NETWORK_PLANNER','NETWORK_ENGINEER') NOT NULL,
  status ENUM('INVITED','ACTIVE','DISABLED') NOT NULL DEFAULT 'INVITED',
  totp_secret_enc VARBINARY(255) NULL,
  totp_enabled_at DATETIME NULL,
  totp_last_step BIGINT NOT NULL DEFAULT 0,
  failed_mfa INT NOT NULL DEFAULT 0,
  locked_until DATETIME NULL,
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_users_email (email),
  KEY ix_users_operator (operator_id, role),
  CONSTRAINT fk_users_operator FOREIGN KEY (operator_id) REFERENCES operators(id),
  CONSTRAINT ck_users_scope CHECK ((role = 'SUPER_ADMIN' AND operator_id IS NULL) OR (role <> 'SUPER_ADMIN' AND operator_id IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE customers (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT UNSIGNED NOT NULL,
  customer_code VARCHAR(20) NOT NULL,
  customer_type ENUM('INDIVIDUAL','BUSINESS') NOT NULL,
  display_name VARCHAR(150) NOT NULL,
  contact_name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL,
  mobile CHAR(10) NOT NULL,
  address_line VARCHAR(255) NOT NULL,
  gstin CHAR(15) NULL,
  tenant_code VARCHAR(20) NULL,
  status ENUM('TRIAL','ACTIVE','SUSPENDED','CLOSED') NOT NULL DEFAULT 'TRIAL',
  kyc_status ENUM('NOT_SUBMITTED','PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'NOT_SUBMITTED',
  kyc_deadline DATE NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cust_code (operator_id, customer_code),
  UNIQUE KEY uq_cust_gstin (operator_id, gstin),
  UNIQUE KEY uq_cust_tenant (tenant_code),
  KEY ix_cust_status (operator_id, status),
  CONSTRAINT fk_cust_operator FOREIGN KEY (operator_id) REFERENCES operators(id),
  CONSTRAINT ck_cust_business CHECK (
    (customer_type = 'INDIVIDUAL' AND gstin IS NULL AND tenant_code IS NULL) OR
    (customer_type = 'BUSINESS' AND gstin IS NOT NULL AND tenant_code IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE customer_users (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL,
  role ENUM('ACCOUNT_ADMIN','BILLING_MANAGER','TECHNICIAN','VIEWER') NOT NULL,
  perm_devices TINYINT(1) NOT NULL DEFAULT 0,
  perm_plans TINYINT(1) NOT NULL DEFAULT 0,
  perm_billing TINYINT(1) NOT NULL DEFAULT 0,
  perm_users TINYINT(1) NOT NULL DEFAULT 0,
  status ENUM('INVITED','ACTIVE','DISABLED') NOT NULL DEFAULT 'INVITED',
  totp_secret_enc VARBINARY(255) NULL,
  totp_enabled_at DATETIME NULL,
  totp_last_step BIGINT NOT NULL DEFAULT 0,
  failed_mfa INT NOT NULL DEFAULT 0,
  locked_until DATETIME NULL,
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cu_email (email),
  KEY ix_cu_customer (customer_id),
  CONSTRAINT fk_cu_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

CREATE TABLE auth_otps (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL,
  principal ENUM('STAFF','CUSTOMER') NOT NULL,
  purpose ENUM('SIGNUP','LOGIN','INVITE') NOT NULL,
  code_hash CHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
  consumed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_otp_email (email, principal, created_at)
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  principal ENUM('STAFF','CUSTOMER') NOT NULL,
  principal_id BIGINT UNSIGNED NOT NULL,
  family_id CHAR(36) NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  revoked_at DATETIME NULL,
  user_agent VARCHAR(255) NULL,
  ip_address VARCHAR(45) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_rt_hash (token_hash),
  KEY ix_rt_family (family_id),
  KEY ix_rt_principal (principal, principal_id)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT UNSIGNED NULL,
  user_id BIGINT UNSIGNED NULL,
  customer_user_id BIGINT UNSIGNED NULL,
  action VARCHAR(60) NOT NULL,
  entity_type VARCHAR(40) NOT NULL,
  entity_id VARCHAR(64) NOT NULL,
  details JSON NULL,
  ip_address VARCHAR(45) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_audit_entity (entity_type, entity_id),
  KEY ix_audit_user (user_id, created_at),
  KEY ix_audit_operator (operator_id, created_at)
) ENGINE=InnoDB;

-- Append-only: single-statement triggers (no DELIMITER needed for Flyway)
CREATE TRIGGER trg_audit_no_update BEFORE UPDATE ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append-only';
CREATE TRIGGER trg_audit_no_delete BEFORE DELETE ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append-only';
