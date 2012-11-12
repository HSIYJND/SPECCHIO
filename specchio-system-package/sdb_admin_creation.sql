-- sdb_admin creation
-- 09.12.2007, ahueni
-- 09.01.2008, ahueni : added the old_password update, added flush privileges
-- 14.01.2007, ahueni : added the insert statement for the specchio_user table
-- 25.05.2009, ahueni : update of the GRANTS, deactivated the INSERT into the specchio_user table
-- 02.-0.2010, ahueni : added GRANT SUPER ON *.* TO sdb_admin;, TRIGGER for MySQL 5.1.6

CREATE USER 'sdb_admin'@'localhost' IDENTIFIED BY 'xXY7!fe@';


-- Added TRIGGER for MySQL 5.1.6
GRANT SELECT, DELETE, INSERT, UPDATE, ALTER, DROP, CREATE, CREATE VIEW, GRANT OPTION, TRIGGER ON specchio.* TO sdb_admin@'localhost';
GRANT SUPER ON *.* TO sdb_admin@'localhost';

-- Activate the following line if you choose a different name for sdb_admin (sdb_admin is contained in the supplied schema by default).
-- INSERT into specchio_user (user, first_name, last_name, email, admin) values ('sdb_admin', '', 'sdb_admin', '', 1);

GRANT INSERT ON mysql.user TO sdb_admin@'localhost';

UPDATE mysql.user SET Reload_priv='Y', Process_priv='Y', Update_priv='Y', Delete_priv='Y', Select_priv='Y' where User='sdb_admin';

FLUSH PRIVILEGES;