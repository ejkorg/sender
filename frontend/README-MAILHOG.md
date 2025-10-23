Local MailHog guide

1) Start MailHog with docker-compose:

   docker compose -f docker-compose.yml -f docker-compose.mailhog.yml up -d mailhog

2) Start the backend with the `mailhog` Spring profile so it uses the local SMTP relay:

   cd backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=mailhog

3) Open MailHog UI at http://localhost:8025 to view captured emails.

Notes:
- MailHog listens on SMTP 1025 and provides a web UI on 8025.
- `application-mailhog.yml` disables auth/starttls because MailHog accepts plain SMTP on 1025.
- The `app.mail.reset-url-base` points to localhost:4200 so reset links in emails are clickable and direct to your local frontend.
