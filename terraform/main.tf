/*
The AWS user used by terraform is granted the AWS managed policy AdministratorAccess.
*/

variable "terraform_access_key" {
  type = string
  description = "Access key of terraform user"
}

variable "terraform_secret_key" {
  type = string
  description = "Secret key for terraform user"
}

terraform {
  backend "s3" {
    bucket = "poca-2020"
    key = "poca-2020"
    region = "eu-west-3"
    dynamodb_table = "poca-tfstates-locks"
  }
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.7.0"
    }
  }
}

provider "aws" {
  region = "eu-west-3"  # Europe (Paris)
  access_key = var.terraform_access_key
  secret_key = var.terraform_secret_key

}

data "aws_ami" "amazon_linux_2" {
  most_recent = true
  owners = ["amazon"]

  filter {
    name = "name"
    values = ["amzn2-ami-hvm-*-x86_64-gp2"]
  }
}
