Foursquare's New Technology Policy

# Why?

Being able to introduce new technologies (programming languages, frameworks/platforms) is great, but there can be significant production and maintenance overhead to new systems:

* If it's a replacement, we need to get it up to our current standards and reeducate everyone who uses the existing one

* If it's an additional system we need to do everything for a replacement AND integrate all of our existing tooling/monitoring/etc so that we can give consistent guidelines for people on call or who are doing development

* If someone leaves or is busy with other projects we don't want projects using the new technology to be left to rot or to find an unwilling new owner

* Even if it can be enabled quickly, software cost is not solely derived from the implementation. Software maintenance has been estimated as roughly ~75% of the **TCO** (Total Cost of Ownership).

This proposal is to give some guidelines on how to introduce a new technology in a way which will be sustainable and give it the best chance of long-term success at **Foursquare**.

# What does this apply to?

There aren't any hard rules. If you're uncertain about whether a new technology falls under this policy your best bet will be to ask in **#serverarch**. Asking in advance of the work can save everyone from writing something that doesn't end up being used.

To give some examples, if a technology meets a few of the criteria below it probably applies. **Note: it doesn't matter if we are building the technology ourselves or integrating a 3rd party one.**

* **It is a new programming language**. These involve a lot of integration work, will have different runtime characteristics, and require new methods of debugging and fire fighting.

* **It involves running a server that will receive a new kind of traffic**. Especially if it being down or broken would take down the site or offline infrastructure.

* **It replaces something we've built a lot on top of**. Something like a new RPC or build system.  

* **It's something people will ****_start_**** building lots of things on top of**. Examples include new distributed computing framework, new databases, new languages.

* **Something which changes the architecture or runtime characteristics of our systems**. Examples include async rpc systems, remote endpoints, etc.

* **Something that duplicates or replaces an industry standard or widely-used open source implementation**. If the technology or project does not directly underpin a core business offering, we should strongly prefer proven open source tools over custom implementations. Custom infrastructure can quickly age into tech debt. Examples include implementing any offering already covered by Apache, Cloud Native Computing Foundation, an active GitHub project with 500+ stars, etc.

# The Process

If you'd like to introduce a new language/framework for a production system you must have your proposal accepted before it can move past the toy/prototype phase. If your proposal is rejected it will likely mean throwing away the work you did. For the best chance at success you should involve the main stakeholders early on rather than surprising them with a request to adopt a running system.

The committee will be made up of:

* The eng-leads group

* Senior individual contributors who work in relevant areas

* Other stakeholders of the new system.

Since this is a large group in most cases the proposal will be assessed by a technology-appropriate subset of members. For example, we would want to make sure our data scientists were well represented if we were introducing a new machine learning platform but we wouldn't necessarily need everyone.

For advice and help with the proposal, the #**serverarch** group is a great resource (**serverarch**@). They should also have a good idea of whether the technology under consideration requires a proposal.

# The Proposal

To make sure you're starting from a sustainable base with your new technology you should try to cover as many of the items below in your proposal as possible. Your proposal does not need cover all of the criteria below to be accepted, especially for non-critical technologies, but the more it does the more convincing it will be.

* **Why:** An argument that the new technology has benefits over existing choices that outweigh its build/maintenance or ongoing integration costs

* **Scope:** A plan for what sorts of systems this technology *should and should not* be used to implement

    * How many estimated SE weeks to build and ship to production?

    * Any part of our stack able to force rewrites/maintenance of the proposed technology?

* **Cost/Benefit analysis **(please be precise, numbers are persuasive)**:**

    * What do we gain by adopting the proposal?

    * What are the costs of not adopting this proposal?

* **Deployment Plan:**

    * **Integration Plan:** A plan for continuous integration, monitoring, logging, deployment and production/usage cookbooks.

    * **Conversion Plan:** If the technology is a replacement, a sketch of a plan for how to do the conversion from the old technology and an estimate of how long we'll be running both technologies side by side (it's okay if the answer is "basically forever", but we want to know)

    * **Rollback Plan:** A sketch of a plan for how we would remove this technology if it didn't work out

* **Owning manager sign-off:** Sign-off from the manager of the team that will be supporting the technology. If you are the manager of that team, check with your manager.

* **Volunteers:** At least four engineers—including at least one member of the appropriate support team—who have signed up to learn how to write production quality code and firefight issues with the system (as appropriate)

    * Unless a volunteer already knows the technology well this means having them write code using this technology, not just doing code reviews.

    * All of the volunteers agree to support the new systems in production, including being on call. On call in this context meaning being available for the appropriate support team (e.g. prod) to contact.

    * It is the job of the volunteers to make the system resilient enough and well documented enough that the support team doesn't need to contact them.

    * The volunteers would be listed on a page of "system owners" that helped find people who know about a technology.

