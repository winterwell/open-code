import csv

class Employee:
    def __init__(self, first_name, last_name, email, location, department):
        self.first_name: str = first_name
        self.last_name: str = last_name
        self.email: str = email
        self.location : str= location
        self.department: str = department

    def __str__(self):
        return f'Employee(name={self.first_name} {self.last_name}, email={self.email})'

    @classmethod
    def from_csv_row(cls, csv_row):
        first_name = csv_row['First Name']
        last_name = csv_row['Last Name']
        email = csv_row['Work Email']
        location = csv_row['Location']
        department = csv_row['Department']
        return cls(first_name, last_name, email, location, department)


def read_employees_from_csv(file_path: str):
    employees = []
    with open(file_path, 'r') as file:
        reader = csv.DictReader(file)
        for row in reader:
            employee = Employee.from_csv_row(row)
            employees.append(employee)
    return employees