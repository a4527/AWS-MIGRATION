variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "repositories" {
  description = "ECR repositories keyed by logical name."
  type = map(object({
    image_tag_mutability = string
    scan_on_push         = bool
    force_delete         = optional(bool, false)
  }))
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
