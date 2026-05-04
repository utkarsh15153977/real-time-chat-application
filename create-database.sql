-- Chat Application Database Setup
-- Run this in PostgreSQL (pgAdmin or psql)

-- Create database
CREATE DATABASE chat_application;

-- Connect to the database
\c chat_application;

-- Create user (if needed)
-- CREATE USER postgres WITH PASSWORD 'newpassword';
-- GRANT ALL PRIVILEGES ON DATABASE chat_application TO postgres;

-- The application will create tables automatically with spring.jpa.hibernate.ddl-auto=update

-- Verify connection
SELECT version();