-- The application now manages its own local key. Retain legacy ciphertext for backup.
ALTER TABLE mail_accounts RENAME COLUMN imap_secret TO legacy_imap_secret;
ALTER TABLE mail_accounts RENAME COLUMN smtp_secret TO legacy_smtp_secret;
ALTER TABLE mail_accounts ADD COLUMN imap_secret VARCHAR(4096);
ALTER TABLE mail_accounts ADD COLUMN smtp_secret VARCHAR(4096);
UPDATE mail_accounts SET enabled = FALSE,
    last_error = '升级后请编辑邮箱，重新输入 IMAP 和 SMTP 密码或授权码，再启用账号';
