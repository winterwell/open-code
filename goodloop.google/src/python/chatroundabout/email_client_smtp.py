import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import configparser

class Email_Client_SMTP:

    def __init__(self, config_filepath: str):
        self.properties = self.load_properties(config_filepath)

    def load_properties(self, file_path):
        # Create an instance of the ConfigParser class
        config = configparser.ConfigParser()

        # Load the properties file
        config.read(file_path)

        # Return the loaded properties as a dictionary
        properties = {}
        for section in config.sections():
            for key, value in config.items(section):
                properties[key] = value

        return properties

    def send_email_message(self, emailContent: str, emailTo: str, emailSubject: str, emailfrom: str = 'alerts@good-loop.com'):
        # Gmail SMTP server details
        properties = self.properties
        smtp_server = properties['emailserver']
        smtp_port = properties['emailport']
        username = properties['emailfrom']
        app_password = properties['emailpassword']

        # Create a MIME multipart message
        message = MIMEMultipart()
        message['From'] = emailfrom
        message['To'] = emailTo
        message['Subject'] = emailSubject

        # Create a MIMEText object with HTML content
        email_body = MIMEText(emailContent, 'html')
    
        message.attach(email_body)

        try:
            # Create a secure SSL/TLS connection with the SMTP server
            server = smtplib.SMTP(smtp_server, smtp_port)
            server.ehlo()
            server.starttls()
            server.login(username, app_password)

            # Send the email
            server.sendmail(username, emailTo, message.as_string())
            print("Email sent successfully!")

        except Exception as e:
            print(f"Error sending email: {e}")