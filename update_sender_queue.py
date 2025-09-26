import datetime

import oracledb
import os
import sys
import configparser
from datetime import datetime
import fnmatch
import logging
import logging.config
import smtplib


def send_mail(mail_to, subject, body):
    smtp = smtplib.SMTP('localhost')
    FROM = "yms.admins@onsemi.com"
    # TO = mail_to
    TO = mail_to
    MSG = (
        f'To: {TO}\r\nSubject:Historical Loading\r\n{subject} Historical Loading for {tester_type} is complete !\n\n{body}\n\nPlease kindly help to validate.\n\nThank you,\n\nData Integration Team')

    # Try to log in to server and send email
    try:
        smtp.sendmail(FROM, TO.split(','), MSG)
    except Exception as e:
        # Print any error messages to stdout
        logger.error("Error: ", str(e))
    finally:
        smtp.quit()


def removeOneLine(file):
    with open(file, 'r+') as fp:
        # read an store all lines into list
        lines = fp.readlines()
        # move file pointer to the beginning of a file
        fp.seek(0)
        # truncate the file
        fp.truncate()

        # start writing lines except the first line
        # lines[1:] from line 2 to last line
        fp.writelines(lines[1:])


def queryData():
    try:
        connection = oracledb.connect(
            user=user,
            password=pw,
            host=host,
            sid=sid,
            port=port
        )

        print("Successfully connected to Oracle Database")
        cursor = connection.cursor()

        query_string = "select lot,id,id_data from all_metadata_view" \
                       " where end_time between ('{0}') AND ('{1}')" \
                       " and tester_type ='{2}'" \
                       " and data_type ='{3}'" \
            .format(start_date, end_date, tester_type, data_type)
        print(query_string)
        # list_dir= os.path.dirname(os.path.abspath(__file__))
        # file_path = os.path.join(output_folder,'list.txt')

        for row in cursor.execute(query_string):
            with open(list_file, 'a+') as f:
                f.write(str(row[1]) + "," + str(row[2]) + '\n')
        # no need to use file close when using with open()
        # f.close()
        cursor.close()
    except Exception as e:
        logger.error("Error: ", str(e))


# for linux implementation
config_file = sys.argv[1]

# for testing in windows
# config_file = os.path.dirname(os.path.abspath(sys.argv[0])) + "\config.ini"

config_obj = configparser.ConfigParser()

config_obj.read(config_file)

dbparam = config_obj["DB_PARAM"]
senderparam = config_obj["SENDER_PARAM"]
dataparam = config_obj["DATA_PARAM"]
log = config_obj["LOG"]
email_recepient = config_obj["EMAIL"]

start_date = dataparam["start_date"]
end_date = dataparam["end_date"]
tester_type = dataparam["tester_type"]
data_type = dataparam["data_type"]

user = dbparam["user"]
pw = dbparam["password"]
host = dbparam["host"]
sid = dbparam["sid"]
service_name = dbparam["service_name"],  # Use service_name if SID is not provided
port = dbparam["port"]
sender_id = senderparam["sender_id"]
# sender_output_dir = senderparam["sender_output_dir"]
number_of_data_to_send = senderparam["number_of_data_to_send"]
count_limit_trigger = senderparam["count_limit_trigger"]
log_file = log["log_file"]

mail_to = email_recepient["recepients"]
message = email_recepient["jira"]
subject = email_recepient["subject"]

list_file = senderparam["list_file"]

logging.basicConfig(filename=log_file,
                    format='%(asctime)s,%(name)s %(levelname)s %(message)s',
                    level=logging.DEBUG)

# logging.config.fileConfig('log.conf')
# logger = logging.getLogger('logger_sender')

logger = logging.getLogger(__name__)
logger.setLevel(logging.DEBUG)

logger.info("Process Start")
logger.info(f"Process ID :{os.getpid()}")

logger.info(f"Checking if file list exist :{list_file}")
if (os.path.exists(list_file)):
    logger.info("file list exist, skipping call to query data list")
else:
    logger.info("file list does not exist, generating file list")
    queryData()

# check if file list is complete. exit program if yes
with open(list_file, 'r+') as f:
    for line in f.readlines():
        print(line)
        if line == "complete":
            sys.exit(0)

file_size = os.path.getsize(list_file)

if file_size == 0:
    logger.info("File has no data. Exiting the program...")
    send_mail(mail_to, subject, message)
    with open(list_file, 'a+') as f:
        f.write('complete')
    sys.exit(0)

# check first the number of files in sender output directory
# fCount This is only feasible if the script run in DataPort prod server
# If run in Colo QA or Dev this is not applicable
# fCount = len(fnmatch.filter(os.listdir(sender_output_dir), '*.gz*'))
# print(sender_output_dir + str(fCount))print(sender_output_dir + str(fCount))
# if fCount > 2:
#    print("file count exceed limit")
# else:
try:
    logger.info(f"Connecting to host : {host}")
    connection = oracledb.connect(
        user=user,
        password=pw,
        host=host,
        sid=sid,
        port=port
    )

    cursor = connection.cursor()

    query_string = "select count(id) as count from DTP_SENDER_QUEUE_ITEM where id_sender='{0}'" \
        .format(sender_id.strip())

    cursor.execute(query_string)
    count = int(cursor.fetchone()[0])

    if count < int(count_limit_trigger):
        print("queue data is less than limit " + str(count_limit_trigger))
        logger.info("queue data is less than limit " + str(count_limit_trigger))
        line_count = 0
        metadata_id = ""
        metadata_id_data = ""
        with open(list_file, 'r+') as f:
            for line in f.readlines():
                if line_count >= int(number_of_data_to_send):
                    break

                try:
                    # get next id for seq
                    seq_string = "select DTP_SENDER_QUEUE_ITEM_SEQ.nextval from dual"
                    cursor.execute(seq_string)
                    seq_id = int(cursor.fetchone()[0])
                    print(str(seq_id))
                    metadata_id = line.split(",")[0]
                    metadata_id_data = line.split(",")[1]
                    # insert data to queue table
                    insert_string = "insert into DTP_SENDER_QUEUE_ITEM (id, id_metadata, id_data, id_sender, record_created)" \
                                    " values  ({0}, {1}, {2}, {3}, '{4}')" \
                        .format(seq_id, metadata_id, metadata_id_data, sender_id.strip(),
                                datetime.now().strftime("%d-%b-%y %I.%M.%S.%f"))
                    print(insert_string)
                    cursor.execute(insert_string)
                    connection.commit()
                    logger.info(f"Done Processing all_metadata_view id : {metadata_id} and id_data :{metadata_id_data}")
                    removeOneLine(list_file)

                except Exception as e:
                    logger.error("Error: ", str(e))

                line_count += 1

    else:
        logger.info("queue data is more than the set limit " + str(count_limit_trigger) + " skipping processing")
    cursor.close()
    connection.close()
except Exception as e:
    print("Error: ", str(e))
    logger.error("Error: ", str(e))

logger.info("Process End")